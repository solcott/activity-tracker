// Generates fixture-repo.car with the reference @atproto/repo, inside the local PDS image (no account or PDS state involved):
//   docker run --rm --platform linux/amd64 -v "$PWD":/fixture:ro --entrypoint node -w /app/services/pds <pds image> /fixture/gen-fixture.mjs > out.json
// then base64-decode out.json's "car" into fixture-repo.car. Its did, key and rev are in RepoFixture.kt. The key is a test-only key.
const NS = 'app.activitytracker';
(async () => {
  const P = '/app/services/pds/node_modules/.pnpm/';
  const { Repo, MemoryBlockstore, WriteOpAction, getFullRepo } = await import(P + '@atproto+repo@file++++app+packages+repo/node_modules/@atproto/repo/dist/index.js');
  const { Secp256k1Keypair } = await import(P + '@atproto+crypto@file++++app+packages+crypto/node_modules/@atproto/crypto/dist/index.js');
  const key = await Secp256k1Keypair.import('9085d2bef69286a6cbb51623c8fa258629945cd55ca705cc4e66700396894e0c');
  const did = 'did:plc:reindexfixture0000000000';
  const storage = new MemoryBlockstore();
  const writes = [];
  const put = (collection, rkey, record) => writes.push({ action: WriteOpAction.Create, collection, rkey, record });
  put(`${NS}.actor.profile`, 'self', { $type: `${NS}.actor.profile`, displayName: 'Fixture' });
  put(`${NS}.graph.follow`, '3kfollow00001', { $type: `${NS}.graph.follow`, subject: 'did:plc:bob', createdAt: '2026-01-01T00:00:00.000Z' });
  put('app.bsky.graph.block', '3kblock000001', { $type: 'app.bsky.graph.block', subject: 'did:plc:carol', createdAt: '2026-01-01T00:00:00.000Z' });
  put('app.bsky.feed.post', '3kpost0000001', { $type: 'app.bsky.feed.post', text: 'not indexed', createdAt: '2026-01-01T00:00:00.000Z' });
  for (let i = 0; i < 40; i++) {
    const rkey = '3kpost' + String(i).padStart(7, '0');
    put(`${NS}.feed.post`, rkey, { $type: `${NS}.feed.post`, text: `post ${i}`, createdAt: '2026-01-02T00:00:00.000Z',
      bytesField: new Uint8Array([1, 2, 3]) });
  }
  const repo = await Repo.create(storage, did, key, writes);
  const chunks = [];
  for await (const c of getFullRepo(storage, repo.cid)) chunks.push(Buffer.from(c));
  const car = Buffer.concat(chunks);
  process.stdout.write(JSON.stringify({ did, key: key.did(), rev: repo.commit.rev, root: repo.cid.toString(), car: car.toString('base64') }));
})().catch((e) => { console.error(e); process.exit(1); });
