-- The 2026-11-24 Spaces alpha writes space URIs as at:space/{did}/{type}/{skey}, not at://{did}/space/{type}/{skey}, and its
-- hosts accept only the new form. Rewrite every stored space URI: space_sync's key and each row's origin (else 'repo'). Only
-- values still in the old form change (a DID holds no '/', so the first '/space/' ends it), so running this again changes nothing.

UPDATE space_sync SET space_uri = 'at:space/' || SUBSTRING(space_uri FROM 6 FOR POSITION('/space/' IN space_uri) - 6) ||
    SUBSTRING(space_uri FROM POSITION('/space/' IN space_uri) + 6)
    WHERE space_uri LIKE 'at://%/space/%';

UPDATE activities SET origin = 'at:space/' || SUBSTRING(origin FROM 6 FOR POSITION('/space/' IN origin) - 6) ||
    SUBSTRING(origin FROM POSITION('/space/' IN origin) + 6)
    WHERE origin LIKE 'at://%/space/%';

UPDATE posts SET origin = 'at:space/' || SUBSTRING(origin FROM 6 FOR POSITION('/space/' IN origin) - 6) ||
    SUBSTRING(origin FROM POSITION('/space/' IN origin) + 6)
    WHERE origin LIKE 'at://%/space/%';

UPDATE likes SET origin = 'at:space/' || SUBSTRING(origin FROM 6 FOR POSITION('/space/' IN origin) - 6) ||
    SUBSTRING(origin FROM POSITION('/space/' IN origin) + 6)
    WHERE origin LIKE 'at://%/space/%';

UPDATE reposts SET origin = 'at:space/' || SUBSTRING(origin FROM 6 FOR POSITION('/space/' IN origin) - 6) ||
    SUBSTRING(origin FROM POSITION('/space/' IN origin) + 6)
    WHERE origin LIKE 'at://%/space/%';

UPDATE comments SET origin = 'at:space/' || SUBSTRING(origin FROM 6 FOR POSITION('/space/' IN origin) - 6) ||
    SUBSTRING(origin FROM POSITION('/space/' IN origin) + 6)
    WHERE origin LIKE 'at://%/space/%';

UPDATE comment_hides SET origin = 'at:space/' || SUBSTRING(origin FROM 6 FOR POSITION('/space/' IN origin) - 6) ||
    SUBSTRING(origin FROM POSITION('/space/' IN origin) + 6)
    WHERE origin LIKE 'at://%/space/%';

UPDATE blocks SET origin = 'at:space/' || SUBSTRING(origin FROM 6 FOR POSITION('/space/' IN origin) - 6) ||
    SUBSTRING(origin FROM POSITION('/space/' IN origin) + 6)
    WHERE origin LIKE 'at://%/space/%';

UPDATE mutes SET origin = 'at:space/' || SUBSTRING(origin FROM 6 FOR POSITION('/space/' IN origin) - 6) ||
    SUBSTRING(origin FROM POSITION('/space/' IN origin) + 6)
    WHERE origin LIKE 'at://%/space/%';

UPDATE activity_streams SET origin = 'at:space/' || SUBSTRING(origin FROM 6 FOR POSITION('/space/' IN origin) - 6) ||
    SUBSTRING(origin FROM POSITION('/space/' IN origin) + 6)
    WHERE origin LIKE 'at://%/space/%';

UPDATE activity_details SET origin = 'at:space/' || SUBSTRING(origin FROM 6 FOR POSITION('/space/' IN origin) - 6) ||
    SUBSTRING(origin FROM POSITION('/space/' IN origin) + 6)
    WHERE origin LIKE 'at://%/space/%';

UPDATE activity_zones SET origin = 'at:space/' || SUBSTRING(origin FROM 6 FOR POSITION('/space/' IN origin) - 6) ||
    SUBSTRING(origin FROM POSITION('/space/' IN origin) + 6)
    WHERE origin LIKE 'at://%/space/%';

UPDATE gear_items SET origin = 'at:space/' || SUBSTRING(origin FROM 6 FOR POSITION('/space/' IN origin) - 6) ||
    SUBSTRING(origin FROM POSITION('/space/' IN origin) + 6)
    WHERE origin LIKE 'at://%/space/%';

UPDATE activity_gear SET origin = 'at:space/' || SUBSTRING(origin FROM 6 FOR POSITION('/space/' IN origin) - 6) ||
    SUBSTRING(origin FROM POSITION('/space/' IN origin) + 6)
    WHERE origin LIKE 'at://%/space/%';

UPDATE activity_stream_fetch_failures SET origin = 'at:space/' || SUBSTRING(origin FROM 6 FOR POSITION('/space/' IN origin) - 6) ||
    SUBSTRING(origin FROM POSITION('/space/' IN origin) + 6)
    WHERE origin LIKE 'at://%/space/%';

UPDATE record_shadows SET origin = 'at:space/' || SUBSTRING(origin FROM 6 FOR POSITION('/space/' IN origin) - 6) ||
    SUBSTRING(origin FROM POSITION('/space/' IN origin) + 6)
    WHERE origin LIKE 'at://%/space/%';
