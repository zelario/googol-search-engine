CREATE TABLE url (
                     url	 VARCHAR(2048),
                     title	 VARCHAR(128) DEFAULT 'Page',
                     citation	 VARCHAR(256) DEFAULT '',
                     updated_at TIMESTAMP NOT NULL DEFAULT now(),
                     PRIMARY KEY(url)
);

CREATE TABLE words (
                       word	 VARCHAR(64),
                       created_at TIMESTAMP NOT NULL DEFAULT now(),
                       PRIMARY KEY(word)
);

CREATE TABLE words_url (
                           words_word VARCHAR(64),
                           url_url	 VARCHAR(2048),
                           created_at TIMESTAMP NOT NULL DEFAULT now(),
                           PRIMARY KEY(words_word,url_url)
);

CREATE TABLE url_url (
                         url_url	 VARCHAR(2048),
                         url_url1 VARCHAR(2048),
                         created_at TIMESTAMP NOT NULL DEFAULT now(),
                         PRIMARY KEY(url_url,url_url1)
);

CREATE TABLE stop_words (
	word VARCHAR(512),
	PRIMARY KEY(word)
);

ALTER TABLE words_url ADD CONSTRAINT words_url_fk1 FOREIGN KEY (words_word) REFERENCES words(word) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE words_url ADD CONSTRAINT words_url_fk2 FOREIGN KEY (url_url) REFERENCES url(url) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE url_url ADD CONSTRAINT url_url_fk1 FOREIGN KEY (url_url) REFERENCES url(url) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE url_url ADD CONSTRAINT url_url_fk2 FOREIGN KEY (url_url1) REFERENCES url(url) DEFERRABLE INITIALLY DEFERRED;

-- Trigger function for stop word detection

CREATE OR REPLACE FUNCTION check_stop_word()
RETURNS TRIGGER AS $$
DECLARE
    url_count INTEGER;
BEGIN
    -- Check if word is already a stop word
    IF EXISTS (SELECT 1 FROM stop_words WHERE word = NEW.words_word) THEN
        -- Skip insert by returning NULL
        RETURN NULL;
    END IF;

    -- Check document frequency
    SELECT COUNT(DISTINCT url_url) INTO url_count FROM words_url WHERE words_word = NEW.words_word;
    IF url_count + 1 > 1000 THEN -- +1 for the new insert
        -- Add to stop_words and remove from index
        INSERT INTO stop_words(word) VALUES (NEW.words_word)
            ON CONFLICT (word) DO NOTHING;
        DELETE FROM words WHERE word = NEW.words_word;
        DELETE FROM words_url WHERE words_word = NEW.words_word;
        RETURN NULL; -- Skip insert
    END IF;

    RETURN NEW; -- Allow insert
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER stop_word_trigger
BEFORE INSERT ON words_url
FOR EACH ROW
EXECUTE FUNCTION check_stop_word();