-- D-031: the baseline talk is analysed free once per uid AND once per device hash. Markers outlive the 7-day sessions
-- table on purpose. key_type is 'uid' or 'device'; session_id lets a failed baseline release its markers.
CREATE TABLE baseline_used (key_type TEXT NOT NULL, key TEXT NOT NULL, session_id TEXT NOT NULL, created_at INT NOT NULL,
  PRIMARY KEY (key_type, key));
