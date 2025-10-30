-- projects

DROP TABLE IF EXISTS projects CASCADE;

CREATE TABLE projects (
    id SERIAL PRIMARY KEY,
    subject TEXT UNIQUE NOT NULL,
    name TEXT
);

-- data_objects

DROP TABLE IF EXISTS data_objects CASCADE;

CREATE TABLE data_objects (
    id SERIAL PRIMARY KEY,
    subject TEXT UNIQUE NOT NULL,
    name TEXT,
    hasObjectSpec TEXT NOT NULL,
    acquisition_start_time TIMESTAMP WITH TIME ZONE,
    acquisition_end_time TIMESTAMP WITH TIME ZONE,
    acquisition_wasAssociatedWith TEXT,
    acquisition_hasSamplingHeight FLOAT,
    wasSubmittedBy TEXT,
    wasAcquiredBy TEXT,
    submission_start_time TIMESTAMP WITH TIME ZONE,
    submission_end_time TIMESTAMP WITH TIME ZONE,
    data_start_time TIMESTAMP WITH TIME ZONE,
    data_end_time TIMESTAMP WITH TIME ZONE,
    hasSha256sum TEXT NOT NULL,
    hasNumberOfRows INTEGER,
    hasSizeInBytes BIGINT,
    hasActualColumnNames TEXT,
    isNextVersionOf INTEGER,
    wasProducedBy TEXT,
    hasDoi TEXT,
    FOREIGN KEY (isNextVersionOf) REFERENCES data_objects(id)
);

-- keywords

DROP TABLE IF EXISTS project_keywords CASCADE;
DROP TABLE IF EXISTS triple_keywords CASCADE;
DROP TABLE IF EXISTS keywords CASCADE;


CREATE TABLE keywords (
    id SERIAL PRIMARY KEY,
    keyword TEXT UNIQUE NOT NULL
);

CREATE TABLE triple_keywords (
    subject TEXT NOT NULL,
    keyword_id INTEGER NOT NULL,
    PRIMARY KEY (subject, keyword_id),
    FOREIGN KEY (keyword_id) REFERENCES keywords(id)
);

CREATE TABLE project_keywords (
    project_id INTEGER NOT NULL,
    keyword_id INTEGER NOT NULL,
    PRIMARY KEY (project_id, keyword_id),
    FOREIGN KEY (project_id) REFERENCES projects(id),
    FOREIGN KEY (keyword_id) REFERENCES keywords(id)
);

-- aggregate_keywords

DROP TABLE IF EXISTS data_object_all_keywords CASCADE;
CREATE TABLE data_object_all_keywords (
    data_object_id INTEGER NOT NULL,
    keyword_id INTEGER NOT NULL,
    PRIMARY KEY (data_object_id, keyword_id),
    FOREIGN KEY (data_object_id) REFERENCES data_objects(id),
    FOREIGN KEY (keyword_id) REFERENCES keywords(id)
);


-- Initial indices

-- projects

CREATE INDEX idx_projects_subject ON projects(subject);

