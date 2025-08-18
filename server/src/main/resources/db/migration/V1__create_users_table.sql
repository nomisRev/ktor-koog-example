CREATE TABLE IF NOT EXISTS users (
    user_id BIGSERIAL PRIMARY KEY,
    subject VARCHAR(256) NOT NULL,
    email VARCHAR(256),
    display_name VARCHAR(256),
    about_me VARCHAR(256),
    CONSTRAINT unique_subject UNIQUE (subject)
);