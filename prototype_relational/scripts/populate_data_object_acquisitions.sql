INSERT INTO data_object_acquisitions (subject, startedAtTime, endedAtTime, wasAssociatedWith, hasSamplingHeight)
SELECT
    wasAcquiredBy as subject,
    MIN(acquisition_start_time) as startedAtTime,
    MAX(acquisition_end_time) as endedAtTime,
    (array_agg(acquisition_wasAssociatedWith ORDER BY acquisition_start_time))[1] as wasAssociatedWith,
    (array_agg(acquisition_hasSamplingHeight ORDER BY acquisition_start_time))[1] as hasSamplingHeight
    FROM data_objects
    WHERE wasAcquiredBy IS NOT NULL
    GROUP BY wasAcquiredBy
    ON CONFLICT (subject) DO UPDATE
    SET
      startedAtTime = EXCLUDED.startedAtTime,
      endedAtTime = EXCLUDED.endedAtTime,
      wasAssociatedWith = EXCLUDED.wasAssociatedWith,
      hasSamplingHeight = EXCLUDED.hasSamplingHeight;
