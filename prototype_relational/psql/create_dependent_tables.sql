-- object_specs
DROP TABLE IF EXISTS object_specs CASCADE;

CREATE TABLE object_specs (
    id SERIAL PRIMARY KEY,
    triple_id INTEGER NOT NULL UNIQUE,
    subject TEXT UNIQUE NOT NULL,
    FOREIGN KEY (triple_id) REFERENCES triples(id)
);

-- projects

DROP TABLE IF EXISTS object_spec_projects CASCADE;
DROP TABLE IF EXISTS projects CASCADE;

CREATE TABLE projects (
    id SERIAL PRIMARY KEY,
    triple_id INTEGER UNIQUE,
    subject TEXT UNIQUE NOT NULL,
    name TEXT,
    FOREIGN KEY (triple_id) REFERENCES triples(id)
);


CREATE TABLE object_spec_projects (
    object_spec_id INTEGER NOT NULL,
    project_id INTEGER NOT NULL,
    PRIMARY KEY (object_spec_id, project_id),
    FOREIGN KEY (object_spec_id) REFERENCES object_specs(id),
    FOREIGN KEY (project_id) REFERENCES projects(id)
);

-- data_objects

DROP TABLE IF EXISTS data_objects CASCADE;

CREATE TABLE data_objects (
    id SERIAL PRIMARY KEY,
    triple_id INTEGER NOT NULL,
    subject TEXT UNIQUE NOT NULL,
    object_spec_id INTEGER NOT NULL,
    name TEXT,
    acquisition_start_time TIMESTAMP WITH TIME ZONE,
    acquisition_end_time TIMESTAMP WITH TIME ZONE,
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
    FOREIGN KEY (triple_id) REFERENCES triples(id),
    FOREIGN KEY (object_spec_id) REFERENCES object_specs(id),
    FOREIGN KEY (isNextVersionOf) REFERENCES data_objects(id)
);

-- keywords

DROP TABLE IF EXISTS project_keywords CASCADE;
DROP TABLE IF EXISTS object_spec_keywords CASCADE;
DROP TABLE IF EXISTS triple_keywords CASCADE;
DROP TABLE IF EXISTS keywords CASCADE;


CREATE TABLE keywords (
    id SERIAL PRIMARY KEY,
    keyword TEXT UNIQUE NOT NULL
);

CREATE TABLE triple_keywords (
    triple_id INTEGER NOT NULL,
    keyword_id INTEGER NOT NULL,
    PRIMARY KEY (triple_id, keyword_id),
    FOREIGN KEY (triple_id) REFERENCES triples(id),
    FOREIGN KEY (keyword_id) REFERENCES keywords(id)
);

CREATE TABLE object_spec_keywords (
    object_spec_id INTEGER NOT NULL,
    keyword_id INTEGER NOT NULL,
    PRIMARY KEY (object_spec_id, keyword_id),
    FOREIGN KEY (object_spec_id) REFERENCES object_specs(id),
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

-- object_specs

CREATE INDEX idx_object_specs_triple ON object_specs(triple_id);
CREATE INDEX idx_object_specs_subject ON object_specs(subject);

-- projects

CREATE INDEX idx_projects_triple ON projects(triple_id);
CREATE INDEX idx_projects_subject ON projects(subject);
CREATE INDEX idx_object_spec_projects_spec ON object_spec_projects(object_spec_id);
CREATE INDEX idx_object_spec_projects_project ON object_spec_projects(project_id);

