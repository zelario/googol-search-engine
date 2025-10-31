DROP TRIGGER stop_word_trigger ON words_url;

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
    IF url_count + 1 > 100 THEN -- +1 for the new insert
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