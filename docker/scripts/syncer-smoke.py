"""Spike: can a service with its own did:web act as an atproto Spaces syncer on the local PDS?

Run through syncer-smoke.sh, which starts the quick tunnel the stub's did:web and notification endpoint are served through.
Standard library plus the openssl CLI only. As <name>.test it creates a member-list space and admits the stub DID as a reader;
then, as the stub, it mints a delegation token and exchanges it with authorizeSpaceSync for a space credential whose request also
registers the stub for notifications (the Nov 12 alpha): the PDS confirms the registration by calling confirmRegistration on the
stub, then sends notifySpaceUpdate, both carrying the stub's token and no service auth. It waits for the repoWrite a new record
triggers and reads the ops back with listRepoOps, checking each commit's MAC: a fresh P-256 key share per request, HPKE secret
export against the author's #atproto_kem key, and an hmac-sha256 HTTP message signature over the Atproto-Space-Commit header.
With --blob it also puts a record referencing an uploaded blob and reads it back through space getBlob with the stub's credential.

Usage: syncer-smoke.py <name> <pds-url> <stub-host> <stub-port> <password> [--blob]   (PLC_URL defaults to http://localhost:2582)
"""

import base64
import hashlib
import hmac
import http.server
import json
import os
import queue
import re
import secrets
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

P256_N = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551
P256_P = 0xFFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF
P256_B = 0x5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B
# SubjectPublicKeyInfo DER for a P-256 key, up to the uncompressed point.
P256_SPKI_PREFIX = bytes.fromhex("3059301306072a8648ce3d020106082a8648ce3d030107034200")
HPKE_VERSION = b"HPKE-v1"
# DHKEM(P-256, HKDF-SHA256) is 0x0010, HKDF-SHA256 0x0001, and the Export-only AEAD 0xFFFF.
KEM_ID = b"KEM" + (0x0010).to_bytes(2, "big")
SUITE_ID = b"HPKE" + (0x0010).to_bytes(2, "big") + (0x0001).to_bytes(2, "big") + (0xFFFF).to_bytes(2, "big")
COMMIT_PROFILE = "atproto-space-commit"
B58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
SPACE_TYPE = "app.activitytracker.smoke"
COLLECTION = "app.activitytracker.smoke.note"


def log(msg):
    print(msg, flush=True)


def fail(msg):
    print(f"FAIL {msg}", file=sys.stderr, flush=True)
    sys.exit(1)


def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def b58(data):
    n = int.from_bytes(data, "big")
    out = ""
    while n:
        n, r = divmod(n, 58)
        out = B58[r] + out
    return "1" * (len(data) - len(data.lstrip(b"\0"))) + out


def b58decode(text):
    n = 0
    for ch in text:
        n = n * 58 + B58.index(ch)
    raw = n.to_bytes((n.bit_length() + 7) // 8, "big") if n else b""
    return b"\0" * (len(text) - len(text.lstrip("1"))) + raw


def p256_point(multibase):
    """The uncompressed point (0x04 || X || Y) of a P-256 Multikey or did:key."""
    data = b58decode(multibase.removeprefix("did:key:").removeprefix("z"))
    if data[:2] != b"\x80\x24" or len(data) != 35:
        fail(f"not a P-256 Multikey: {multibase}")
    x = int.from_bytes(data[3:], "big")
    y = pow((x * x * x - 3 * x + P256_B) % P256_P, (P256_P + 1) // 4, P256_P)
    if y & 1 != data[2] & 1:
        y = P256_P - y
    return b"\x04" + x.to_bytes(32, "big") + y.to_bytes(32, "big")


def u16(n):
    return n.to_bytes(2, "big")


def labeled_extract(suite, salt, label, ikm):
    return hmac.new(salt or b"\0" * 32, HPKE_VERSION + suite + label + ikm, hashlib.sha256).digest()


def labeled_expand(suite, prk, label, info, length=32):
    # One HKDF-Expand block is all 32 bytes need.
    return hmac.new(prk, u16(length) + HPKE_VERSION + suite + label + info + b"\x01", hashlib.sha256).digest()[:length]


def hpke_export_secret(dh, enc, author_point):
    """RFC 9180 base-mode secret export with info "atproto-mac" and an empty exporter context, as @atproto/space's kem.ts."""
    kem_secret = labeled_expand(KEM_ID, labeled_extract(KEM_ID, b"", b"eae_prk", dh), b"shared_secret", enc + author_point)
    psk_id_hash = labeled_extract(SUITE_ID, b"", b"psk_id_hash", b"")
    info_hash = labeled_extract(SUITE_ID, b"", b"info_hash", b"atproto-mac")
    secret = labeled_extract(SUITE_ID, kem_secret, b"secret", b"")
    exporter_secret = labeled_expand(SUITE_ID, secret, b"exp", b"\x00" + psk_id_hash + info_hash)
    return labeled_expand(SUITE_ID, exporter_secret, b"sec", b"")


def jwt_payload(token):
    part = token.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(part + "=" * (-len(part) % 4)))


class P256Key:
    """A P-256 key in a PEM file, signed with through openssl. Signatures are raw r||s with low S, as @atproto/crypto requires."""

    def __init__(self, workdir, name):
        self.path = os.path.join(workdir, f"{name}.pem")
        subprocess.run(["openssl", "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", self.path], check=True)
        der = subprocess.run(["openssl", "ec", "-in", self.path, "-pubout", "-outform", "DER"], check=True, capture_output=True).stdout
        point = der[-65:]
        self.point = point
        x, y = point[1:33], point[33:]
        compressed = bytes([3 if y[-1] & 1 else 2]) + x
        # Multikey / did:key: multicodec p256-pub (0x1200 as a varint), base58btc with the multibase "z" prefix.
        self.multibase = "z" + b58(b"\x80\x24" + compressed)
        self.did_key = "did:key:" + self.multibase

    def sign(self, data):
        der = subprocess.run(["openssl", "dgst", "-sha256", "-sign", self.path], input=data, check=True, capture_output=True).stdout
        # SEQUENCE { INTEGER r, INTEGER s }
        i = 2 if der[1] < 0x80 else 3
        rlen = der[i + 1]
        r = int.from_bytes(der[i + 2 : i + 2 + rlen], "big")
        j = i + 2 + rlen
        s = int.from_bytes(der[j + 2 : j + 2 + der[j + 1]], "big")
        if s > P256_N // 2:
            s = P256_N - s
        return r.to_bytes(32, "big") + s.to_bytes(32, "big")


def xrpc(pds, method, verb="GET", params=None, body=None, headers=None, raw=None, content_type=None):
    """One XRPC call; returns (status, parsed JSON or raw bytes, response headers). Never raises on an HTTP error status."""
    url = f"{pds}/xrpc/{method}"
    if params:
        url += "?" + urllib.parse.urlencode(params)
    data = None
    hdrs = dict(headers or {})
    if body is not None:
        data = json.dumps(body).encode()
        hdrs["Content-Type"] = "application/json"
    elif raw is not None:
        data = raw
        hdrs["Content-Type"] = content_type or "application/octet-stream"
    req = urllib.request.Request(url, data=data, headers=hdrs, method=verb)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            status, payload, rh = resp.status, resp.read(), dict(resp.headers)
    except urllib.error.HTTPError as e:
        status, payload, rh = e.code, e.read(), dict(e.headers)
    try:
        return status, json.loads(payload), rh
    except ValueError:
        return status, payload, rh


def must(result, what):
    status, payload, _ = result
    if not 200 <= status < 300:
        fail(f"{what}: HTTP {status} {str(payload)[:400]}")
    return payload


def http_signature(key, authorization, audience):
    """The RFC 9421 signature @atproto/space puts on credentialed reads (credential requests carry none)."""
    params = '("authorization" "atproto-space-audience");tag="atproto-space-credential"'
    lines = [f'"authorization": {authorization}', f'"atproto-space-audience": {audience}', f'"@signature-params": {params}']
    sig = base64.b64encode(key.sign("\n".join(lines).encode())).decode()
    return {"Signature-Input": f"atproto-space-credential={params}", "Signature": f"atproto-space-credential=:{sig}:"}


def credential_headers(credential, audience, binding):
    authorization = f"Atproto-Space-Credential {credential}"
    return {"Authorization": authorization, "Atproto-Space-Audience": audience, **http_signature(binding, authorization, audience)}


def header(headers, name):
    return next((v for k, v in headers.items() if k.lower() == name.lower()), None)


class RepoMac:
    """One listRepoOps request's MAC: a fresh ephemeral key agreed with the author's #atproto_kem point, as @atproto/space's encapsulate."""

    def __init__(self, workdir, author_point):
        eph = P256Key(workdir, f"kem-{secrets.token_hex(4)}")
        peer = os.path.join(workdir, f"peer-{secrets.token_hex(4)}.der")
        with open(peer, "wb") as f:
            f.write(P256_SPKI_PREFIX + author_point)
        dh = subprocess.run(
            ["openssl", "pkeyutl", "-derive", "-inkey", eph.path, "-peerkey", peer, "-peerform", "DER"], check=True, capture_output=True
        ).stdout
        self.secret = hpke_export_secret(dh, eph.point, author_point)
        self.key_share = eph.did_key
        self.params = f'("{COMMIT_PROFILE}";sf);keyid="{eph.did_key}";tag="{COMMIT_PROFILE}"'
        self.header = f"mac={self.params}"

    def check(self, response_headers, space, repo, commit):
        """Fails unless the response carries one MAC tagged for commits, over an Atproto-Space-Commit header describing this commit."""
        if commit["space"] != space or commit["repo"] != repo:
            fail(f"commit is for {commit['repo']} in {commit['space']}")
        inputs = header(response_headers, "Signature-Input") or ""
        signatures = header(response_headers, "Signature") or ""
        covered = header(response_headers, "Atproto-Space-Commit")
        tagged = [m for m in re.finditer(r'([a-z*][a-z0-9_.*-]*)=(\([^)]*\)[^,]*)', inputs) if f'tag="{COMMIT_PROFILE}"' in m.group(2)]
        if len(tagged) != 1 or covered is None:
            fail(f"listRepoOps answered without one commit MAC: {inputs!r} {covered!r}")
        label, params = tagged[0].group(1), tagged[0].group(2).strip()
        if not params.startswith(f'("{COMMIT_PROFILE}";sf)') or f'keyid="{self.key_share}"' not in params:
            fail(f"commit MAC covers or names the wrong thing: {params!r}")
        m = re.search(rf"(?:^|,\s*){re.escape(label)}=:([A-Za-z0-9+/=]+):", signatures)
        if not m:
            fail(f"no signature under {label}: {signatures!r}")
        # The `sf` flag covers the header re-serialized; the reference host already sends it in that canonical form.
        base = f'"{COMMIT_PROFILE}";sf: {covered.strip()}\n"@signature-params": {params}'.encode()
        if not hmac.compare_digest(hmac.new(self.secret, base, hashlib.sha256).digest(), base64.b64decode(m.group(1))):
            fail("commit MAC doesn't verify")
        b64 = commit["data"]["$bytes"]
        data = base64.b64encode(base64.b64decode(b64 + "=" * (-len(b64) % 4))).decode()
        want = f'ver={commit["ver"]}, repo="{repo}", space="{space}", rev="{commit["rev"]}", data=:{data}:'
        if covered.strip() != want:
            fail(f"Atproto-Space-Commit doesn't describe the commit: {covered!r} != {want!r}")


def start_stub(port, did_doc, notifications, tokens):
    """The stub's did.json, and the two calls a space host makes to a registered syncer, each answered only with a token it issued."""

    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_GET(self):
            if self.path == "/.well-known/did.json":
                body = json.dumps(did_doc).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            else:
                self.send_response(404)
                self.end_headers()

        def do_POST(self):
            length = int(self.headers.get("Content-Length") or 0)
            raw = self.rfile.read(length)
            notifications.put({"path": self.path, "headers": dict(self.headers), "body": raw.decode(errors="replace")})
            try:
                body = json.loads(raw)
            except ValueError:
                body = {}
            if tokens.get(body.get("space")) != body.get("token"):
                answer, status = {"error": "InvalidToken", "message": "not a token this syncer issued for that space"}, 400
            elif self.path.startswith("/xrpc/com.atproto.space.confirmRegistration"):
                answer, status = {"challenge": body.get("challenge")}, 200
            else:
                answer, status = {}, 200
            out = json.dumps(answer).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(out)))
            self.end_headers()
            self.wfile.write(out)

    server = http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def wait_for_notification(notifications, timeout, seen):
    """The next repoWrite notifySpaceUpdate; every call to the stub is also recorded in `seen`."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            note = notifications.get(timeout=max(0.1, deadline - time.time()))
        except queue.Empty:
            break
        seen.append(note)
        if not note["path"].startswith("/xrpc/com.atproto.space.notifySpaceUpdate"):
            log(f"     (stub got {note['path']})")
            continue
        body = json.loads(note["body"])
        update = body.get("update", {})
        if update.get("$type") != "com.atproto.space.defs#repoWrite":
            log(f"     (stub got a {update.get('$type')} update, waiting for a repoWrite)")
            continue
        return note, body
    return None, None


def main():
    if len(sys.argv) < 6:
        fail(__doc__)
    name, pds, stub_host, stub_port, password = sys.argv[1:6]
    with_blob = "--blob" in sys.argv[6:]
    stub_did = f"did:web:{stub_host}"
    endpoint = f"https://{stub_host}"
    workdir = tempfile.mkdtemp(prefix="syncer-smoke-")

    signing = P256Key(workdir, "signing")  # published as the stub's #atproto key; signs delegation tokens
    binding = P256Key(workdir, "binding")  # the credential is bound to it; signs credentialed requests
    did_doc = {
        "@context": ["https://www.w3.org/ns/did/v1", "https://w3id.org/security/multikey/v1"],
        "id": stub_did,
        "verificationMethod": [
            {"id": f"{stub_did}#atproto", "type": "Multikey", "controller": stub_did, "publicKeyMultibase": signing.multibase}
        ],
    }
    notifications = queue.Queue()
    tokens = {}
    seen = []
    start_stub(int(stub_port), did_doc, notifications, tokens)
    log(f"ok   stub {stub_did} serving did.json (#atproto {signing.multibase[:16]}...)")

    session = must(
        xrpc(pds, "com.atproto.server.createSession", "POST", body={"identifier": f"{name}.test", "password": password}),
        "createSession",
    )
    did, owner = session["did"], {"Authorization": f"Bearer {session['accessJwt']}"}
    skey = "syncer"
    space = f"at://{did}/space/{SPACE_TYPE}/{skey}"
    log(f"ok   signed in as {name}.test ({did})")

    status, payload, _ = xrpc(
        pds,
        "com.atproto.space.createSpace",
        "POST",
        body={"spaceType": SPACE_TYPE, "skey": skey, "policy": {"$type": "com.atproto.memberspace.policy"}},
        headers=owner,
    )
    if status != 200 and not (isinstance(payload, dict) and payload.get("error") == "SpaceAlreadyExists"):
        fail(f"createSpace: HTTP {status} {payload}")
    must(
        xrpc(
            pds,
            "com.atproto.memberspace.putMembers",
            "POST",
            body={"space": space, "members": [{"did": stub_did, "read": True, "write": False}]},
            headers=owner,
        ),
        "putMembers",
    )
    log(f"ok   space {space} with the stub as a reader")

    # 1. Delegation token signed by the stub's own key, exchanged at the authority's PDS for a credential bound to `binding`.
    now = int(time.time())
    header = {"alg": "ES256", "typ": "atproto-space-delegation+jwt", "kid": "#atproto"}
    claims = {
        "iss": stub_did,
        "sub": space,
        "aud": f"{did}#atproto_pds",
        "iat": now,
        "exp": now + 60,
        "jti": secrets.token_hex(16),
    }
    signing_input = f"{b64url(json.dumps(header).encode())}.{b64url(json.dumps(claims).encode())}"
    delegation = f"{signing_input}.{b64url(signing.sign(signing_input.encode()))}"
    # 2. The same request registers the stub for notifications, with a token only it and the PDS know.
    tokens[space] = secrets.token_urlsafe(32)
    notify = {"endpoint": endpoint, "token": tokens[space]}
    exchange = {"space": space, "credentialKey": binding.did_key, "delegationToken": delegation, "notify": notify}
    authorized = must(
        xrpc(pds, "com.atproto.space.authorizeSpaceSync", "POST", body=exchange),
        "authorizeSpaceSync with the stub's delegation token and notify",
    )
    credential = authorized["credential"]
    cred = jwt_payload(credential)
    log(
        f"ok   authorizeSpaceSync: iss={cred['iss']} cnf.kid={cred.get('cnf', {}).get('kid', '')[:24]}... "
        f"lifetime={cred['exp'] - cred['iat']}s spaceRev={authorized.get('spaceRev')}"
    )
    while not notifications.empty():
        seen.append(notifications.get())
    confirms = [n for n in seen if n["path"].startswith("/xrpc/com.atproto.space.confirmRegistration")]
    if not confirms:
        fail("the PDS registered the stub without calling confirmRegistration")
    log(f"ok   confirmRegistration answered: {confirms[0]['body'][:160]}")

    status, payload, _ = xrpc(pds, "com.atproto.space.authorizeSpaceSync", "POST", body=exchange)
    if status < 400:
        fail("authorizeSpaceSync accepted a replayed delegation token")
    log(f"ok   replayed delegation token refused: {payload.get('error') if isinstance(payload, dict) else status}")

    creds = credential_headers(credential, did, binding)

    plc = os.environ.get("PLC_URL", "http://localhost:2582")
    with urllib.request.urlopen(f"{plc}/{did}", timeout=30) as resp:
        author_doc = json.loads(resp.read())
    kem = next((m for m in author_doc.get("verificationMethod", []) if m["id"].endswith("#atproto_kem")), None)
    if not kem:
        fail(f"{did}'s DID document publishes no #atproto_kem key: {json.dumps(author_doc)[:400]}")
    author_kem = p256_point(kem["publicKeyMultibase"])
    log(f"ok   {did} publishes #atproto_kem {kem['publicKeyMultibase'][:16]}...")

    def list_repo_ops(params, what):
        mac = RepoMac(workdir, author_kem)
        status, page, rh = xrpc(pds, "com.atproto.space.listRepoOps", params=params, headers={**creds, "Accept-Signature": mac.header})
        page = must((status, page, rh), what)
        mac.check(rh, space, did, page["commit"])
        return page

    # 3. Write as the authority and wait for the update.
    marker = f"syncer-{int(time.time())}"
    rkey = f"n{int(time.time())}"
    put = must(
        xrpc(
            pds,
            "com.atproto.space.putRecord",
            "POST",
            body={"space": space, "repo": did, "collection": COLLECTION, "rkey": rkey, "record": {"$type": COLLECTION, "text": marker}},
            headers=owner,
        ),
        "putRecord",
    )
    log(f"ok   putRecord {rkey}: {json.dumps(put)[:200]}")

    note, body = wait_for_notification(notifications, 60, seen)
    if not note:
        fail("no repoWrite notifySpaceUpdate reached the stub within 60 s")
    log(f"ok   notifySpaceUpdate received: {json.dumps(body['update'])}")
    if body.get("token") != tokens[space] or note["headers"].get("Authorization"):
        fail("notifySpaceUpdate should carry the registered token and no service auth")

    # 4. Pull the ops with the credential.
    status, payload, _ = xrpc(pds, "com.atproto.space.listRepoOps", params={"space": space, "repo": did}, headers=creds)
    if status < 400:
        fail("listRepoOps answered a credentialed read without Accept-Signature")
    log(f"ok   listRepoOps without Accept-Signature refused: {payload.get('error') if isinstance(payload, dict) else status}")
    ops = list_repo_ops({"space": space, "repo": did}, "listRepoOps")
    found = [op for op in ops["ops"] if op["rkey"] == rkey]
    if not found or found[0].get("value", {}).get("text") != marker:
        fail(f"listRepoOps didn't return the new record: {json.dumps(ops)[:400]}")
    log(f"ok   listRepoOps: {len(ops['ops'])} ops, new record rev {found[0]['rev']}, commit rev {ops.get('commit', {}).get('rev')} (MAC verified)")
    since = ops["ops"][-1]["rev"]
    tail = list_repo_ops({"space": space, "repo": did, "since": since}, "listRepoOps since")
    log(f"ok   listRepoOps since {since}: {len(tail['ops'])} ops")

    status, payload, _ = xrpc(pds, "com.atproto.space.listRepoOps", params={"space": space, "repo": did})
    if status < 400:
        fail("listRepoOps answered without credentials")
    log(f"ok   listRepoOps refused without credentials: {payload.get('error') if isinstance(payload, dict) else status}")

    # 5. Delete: notification and a delete op.
    must(
        xrpc(pds, "com.atproto.space.deleteRecord", "POST", body={"space": space, "repo": did, "collection": COLLECTION, "rkey": rkey}, headers=owner),
        "deleteRecord",
    )
    note, body = wait_for_notification(notifications, 60, seen)
    if not note:
        fail("no repoWrite for the delete within 60 s")
    tail = list_repo_ops({"space": space, "repo": did, "since": since}, "listRepoOps after delete")
    log(f"ok   delete notified (rev {body['update']['repoRev']}); ops since: {json.dumps(tail['ops'])[:300]}")

    if with_blob:
        blob_bytes = os.urandom(2048)
        uploaded = must(
            xrpc(pds, "com.atproto.repo.uploadBlob", "POST", raw=blob_bytes, content_type="image/jpeg", headers=owner),
            "uploadBlob (repo)",
        )["blob"]
        cid = uploaded["ref"]["$link"]
        log(f"ok   uploadBlob to the repo: {cid} ({uploaded['size']} bytes)")
        brkey = f"b{int(time.time())}"
        must(
            xrpc(
                pds,
                "com.atproto.space.putRecord",
                "POST",
                body={
                    "space": space,
                    "repo": did,
                    "collection": COLLECTION,
                    "rkey": brkey,
                    "record": {"$type": COLLECTION, "text": "with a blob", "photo": uploaded},
                },
                headers=owner,
            ),
            "putRecord referencing the repo-uploaded blob",
        )
        log(f"ok   space putRecord {brkey} referencing it")
        status, got, rh = xrpc(pds, "com.atproto.space.getBlob", params={"space": space, "repo": did, "cid": cid}, headers=creds)
        if status != 200 or got != blob_bytes:
            fail(f"space getBlob with the stub's credential: HTTP {status} {str(got)[:200]}")
        log(f"ok   space getBlob with the stub's credential: {len(got)} bytes match, Content-Type {rh.get('Content-Type')}")
        status, got, _ = xrpc(pds, "com.atproto.sync.getBlob", params={"did": did, "cid": cid})
        log(f"     public com.atproto.sync.getBlob for the same CID: HTTP {status} ({'served' if status == 200 else got})")
        status, got, _ = xrpc(pds, "com.atproto.space.getBlob", params={"space": space, "repo": did, "cid": cid})
        log(f"     space getBlob without credentials: HTTP {status} {got.get('error') if isinstance(got, dict) else ''}")
        must(
            xrpc(pds, "com.atproto.space.deleteRecord", "POST", body={"space": space, "repo": did, "collection": COLLECTION, "rkey": brkey}, headers=owner),
            "deleteRecord (blob record)",
        )
        status, got, _ = xrpc(pds, "com.atproto.space.getBlob", params={"space": space, "repo": did, "cid": cid}, headers=creds)
        log(f"ok   after deleting the record, space getBlob: HTTP {status} {got.get('error') if isinstance(got, dict) else ''}")

    log(f"Syncer smoke check passed on {pds}")


if __name__ == "__main__":
    main()
