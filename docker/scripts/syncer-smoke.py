"""Spike: can a service with its own did:web act as an atproto Spaces syncer on the local PDS?

Run through syncer-smoke.sh, which starts the quick tunnel the stub's did:web is served through. Standard library plus the
openssl CLI only. As <name>.test it creates a member-list space and admits the stub DID as a reader; then, as the stub, it mints
a delegation token, exchanges it for a space credential, registers for write notifications, waits for the notifyWrite a new
record triggers and reads the ops back with listRepoOps. With --blob it also puts a record referencing an uploaded blob and
reads the blob back through space getBlob with the stub's credential.

Usage: syncer-smoke.py <name> <pds-url> <stub-host> <stub-port> <password> [--blob]
"""

import base64
import hashlib
import http.server
import json
import os
import queue
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
B58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
SPACE_TYPE = "app.activitytracker.smoke"
COLLECTION = "app.activitytracker.smoke.note"
SYNCER_FRAGMENT = "activitytracker_space_syncer"


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


def http_signature(key, authorization, audience=None):
    """The RFC 9421 signature @atproto/space puts on credential requests (keyid, no audience) and credentialed reads."""
    if audience is None:
        params = f'("authorization");keyid="{key.did_key}"'
        lines = [f'"authorization": {authorization}']
    else:
        params = '("authorization" "atproto-space-audience")'
        lines = [f'"authorization": {authorization}', f'"atproto-space-audience": {audience}']
    lines.append(f'"@signature-params": {params}')
    sig = base64.b64encode(key.sign("\n".join(lines).encode())).rstrip(b"=").decode()
    return {"Signature-Input": f"atproto-space={params}", "Signature": f"atproto-space=:{sig}:"}


def credential_headers(credential, audience, binding):
    authorization = f"Atproto-Space {credential}"
    return {"Authorization": authorization, "Atproto-Space-Audience": audience, **http_signature(binding, authorization, audience)}


def start_stub(port, did_doc, notifications):
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
            body = self.rfile.read(length)
            notifications.put({"path": self.path, "headers": dict(self.headers), "body": body.decode(errors="replace")})
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", "2")
            self.end_headers()
            self.wfile.write(b"{}")

    server = http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def wait_for_notification(notifications, timeout, want_rev=None):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            note = notifications.get(timeout=max(0.1, deadline - time.time()))
        except queue.Empty:
            break
        if not note["path"].startswith("/xrpc/com.atproto.space.notifyWrite"):
            log(f"     (stub got {note['path']}, ignoring)")
            continue
        body = json.loads(note["body"])
        if want_rev and body.get("repoRev") != want_rev:
            log(f"     (stub got notifyWrite for rev {body.get('repoRev')}, waiting for {want_rev})")
            continue
        return note, body
    return None, None


def main():
    if len(sys.argv) < 6:
        fail(__doc__)
    name, pds, stub_host, stub_port, password = sys.argv[1:6]
    with_blob = "--blob" in sys.argv[6:]
    stub_did = f"did:web:{stub_host}"
    service = f"{stub_did}#{SYNCER_FRAGMENT}"
    workdir = tempfile.mkdtemp(prefix="syncer-smoke-")

    signing = P256Key(workdir, "signing")  # published as the stub's #atproto key; signs delegation tokens
    binding = P256Key(workdir, "binding")  # the credential is bound to it; signs credentialed requests
    did_doc = {
        "@context": ["https://www.w3.org/ns/did/v1", "https://w3id.org/security/multikey/v1"],
        "id": stub_did,
        "verificationMethod": [
            {"id": f"{stub_did}#atproto", "type": "Multikey", "controller": stub_did, "publicKeyMultibase": signing.multibase}
        ],
        "service": [{"id": f"#{SYNCER_FRAGMENT}", "type": "AtprotoSpaceSyncer", "serviceEndpoint": f"https://{stub_host}"}],
    }
    notifications = queue.Queue()
    start_stub(int(stub_port), did_doc, notifications)
    log(f"ok   stub {stub_did} serving did.json (#atproto {signing.multibase[:16]}...)")

    session = must(
        xrpc(pds, "com.atproto.server.createSession", "POST", body={"identifier": f"{name}.test", "password": password}),
        "createSession",
    )
    did, owner = session["did"], {"Authorization": f"Bearer {session['accessJwt']}"}
    skey = "syncer"
    space = f"at://{did}/space/{SPACE_TYPE}/{skey}"
    log(f"ok   signed in as {name}.test ({did})")

    policy = {"$type": "com.atproto.simplespace.defs#memberListPolicy"}
    status, payload, _ = xrpc(
        pds,
        "com.atproto.simplespace.createSpace",
        "POST",
        body={
            "spaceType": SPACE_TYPE,
            "skey": skey,
            "readPolicy": policy,
            "writePolicy": policy,
            "appAccess": {"$type": "com.atproto.simplespace.defs#open"},
        },
        headers=owner,
    )
    if status != 200 and not (isinstance(payload, dict) and payload.get("error") == "SpaceAlreadyExists"):
        fail(f"createSpace: HTTP {status} {payload}")
    must(
        xrpc(pds, "com.atproto.simplespace.putMember", "POST", body={"space": space, "did": stub_did, "read": True, "write": False}, headers=owner),
        "putMember",
    )
    log(f"ok   space {space} with the stub as a reader")

    # 1. Delegation token signed by the stub's own key, exchanged at the authority's PDS for a credential bound to `binding`.
    now = int(time.time())
    header = {"alg": "ES256", "typ": "atproto-space-delegation+jwt", "kid": "#atproto"}
    claims = {
        "iss": stub_did,
        "sub": space,
        "aud": f"{did}#atproto_space_host",
        "iat": now,
        "exp": now + 60,
        "jti": secrets.token_hex(16),
    }
    signing_input = f"{b64url(json.dumps(header).encode())}.{b64url(json.dumps(claims).encode())}"
    delegation = f"{signing_input}.{b64url(signing.sign(signing_input.encode()))}"
    authorization = f"Bearer {delegation}"
    credential = must(
        xrpc(
            pds,
            "com.atproto.space.getSpaceCredential",
            "POST",
            body={"space": space},
            headers={"Authorization": authorization, **http_signature(binding, authorization)},
        ),
        "getSpaceCredential with the stub's delegation token",
    )["credential"]
    cred = jwt_payload(credential)
    log(f"ok   getSpaceCredential: iss={cred['iss']} cnf.kid={cred.get('cnf', {}).get('kid', '')[:24]}... lifetime={cred['exp'] - cred['iat']}s")

    status, payload, _ = xrpc(
        pds,
        "com.atproto.space.getSpaceCredential",
        "POST",
        body={"space": space},
        headers={"Authorization": authorization, **http_signature(binding, authorization)},
    )
    if status < 400:
        fail("getSpaceCredential accepted a replayed delegation token")
    log(f"ok   replayed delegation token refused: {payload.get('error') if isinstance(payload, dict) else status}")

    creds = credential_headers(credential, did, binding)

    # 2. Register for notifications, then write as the authority and wait for the callout.
    registered = must(
        xrpc(pds, "com.atproto.space.registerNotify", "POST", body={"space": space, "service": service}, headers=creds),
        "registerNotify",
    )
    log(f"ok   registerNotify {service}: expiresAt {registered.get('expiresAt')}")

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

    note, body = wait_for_notification(notifications, 60)
    if not note:
        fail("no notifyWrite reached the stub within 60 s")
    auth = note["headers"].get("Authorization", "")
    service_auth = jwt_payload(auth.split(" ", 1)[1]) if auth.startswith("Bearer ") else {}
    log(f"ok   notifyWrite received: {json.dumps(body)}")
    log(f"     service auth: iss={service_auth.get('iss')} aud={service_auth.get('aud')} lxm={service_auth.get('lxm')}")
    if service_auth.get("iss") != did or service_auth.get("aud") != service:
        fail("notifyWrite service auth isn't from the authority to the registered service id")

    # 3. Pull the ops with the credential.
    ops = must(xrpc(pds, "com.atproto.space.listRepoOps", params={"space": space, "repo": did}, headers=creds), "listRepoOps")
    found = [op for op in ops["ops"] if op["rkey"] == rkey]
    if not found or found[0].get("value", {}).get("text") != marker:
        fail(f"listRepoOps didn't return the new record: {json.dumps(ops)[:400]}")
    log(f"ok   listRepoOps: {len(ops['ops'])} ops, new record rev {found[0]['rev']}, commit rev {ops.get('commit', {}).get('rev')}")
    since = ops["ops"][-1]["rev"]
    tail = must(
        xrpc(pds, "com.atproto.space.listRepoOps", params={"space": space, "repo": did, "since": since}, headers=creds),
        "listRepoOps since",
    )
    log(f"ok   listRepoOps since {since}: {len(tail['ops'])} ops")

    status, payload, _ = xrpc(pds, "com.atproto.space.listRepoOps", params={"space": space, "repo": did})
    if status < 400:
        fail("listRepoOps answered without credentials")
    log(f"ok   listRepoOps refused without credentials: {payload.get('error') if isinstance(payload, dict) else status}")

    # 4. Delete: notification and a delete op.
    must(
        xrpc(pds, "com.atproto.space.deleteRecord", "POST", body={"space": space, "repo": did, "collection": COLLECTION, "rkey": rkey}, headers=owner),
        "deleteRecord",
    )
    note, body = wait_for_notification(notifications, 60)
    if not note:
        fail("no notifyWrite for the delete within 60 s")
    tail = must(
        xrpc(pds, "com.atproto.space.listRepoOps", params={"space": space, "repo": did, "since": since}, headers=creds),
        "listRepoOps after delete",
    )
    log(f"ok   delete notified (rev {body['repoRev']}); ops since: {json.dumps(tail['ops'])[:300]}")

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
