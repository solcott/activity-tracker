## REMOVED Requirements

### Requirement: Stream zones follow public training
**Reason**: Zone snapshots move to their own `feed.activityZones` record, stored at the narrower of the charts and training visibility, so who sees them follows the training tier (Followers and Mutuals included) instead of needing training to be public.
**Migration**: See `feed/activity-zones`. Zone snapshots inline in existing streams records are no longer served; re-import an activity to get its zones back.
