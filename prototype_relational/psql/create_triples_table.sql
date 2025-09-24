DROP TABLE IF EXISTS triples CASCADE;

CREATE TABLE triples (
  id SERIAL PRIMARY KEY,
  subject TEXT UNIQUE NOT NULL,
  properties JSONB NOT NULL
);

CREATE INDEX idx_subject ON triples(subject);
CREATE INDEX idx_properties_gin ON triples USING GIN (properties);
