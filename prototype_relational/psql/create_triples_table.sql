DROP TABLE IF EXISTS triples CASCADE;

CREATE TABLE triples (
  id INTEGER PRIMARY KEY,
  subject TEXT UNIQUE NOT NULL,
  properties JSON NOT NULL
);

CREATE INDEX idx_subject ON triples(subject);
CREATE INDEX idx_properties ON triples(properties);
