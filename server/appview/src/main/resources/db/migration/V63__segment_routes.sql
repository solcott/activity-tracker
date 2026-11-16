-- segments/segment-routes: segments of one kind that follow the same path, in any sport group and either direction, form a route named by
-- its earliest-indexed live member. route_uri names it, derived by the indexer; NULL until a segment indexed before routes is backfilled
-- (SegmentRoutes.assignMissing), meaning the segment is alone.
ALTER TABLE segments ADD COLUMN route_uri VARCHAR(512);
CREATE INDEX segments_route_uri_idx ON segments (route_uri);
