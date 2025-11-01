DROP TRIGGER stop_word_trigger ON words_url;

CREATE OR REPLACE FUNCTION check_stop_word()
RETURNS TRIGGER AS $$
DECLARE
    url_count INTEGER;
BEGIN
    IF EXISTS (SELECT 1 FROM stop_words WHERE word = NEW.words_word) THEN
        RETURN NULL;
    END IF;

    SELECT COUNT(DISTINCT url_url) INTO url_count FROM words_url WHERE words_word = NEW.words_word;
    IF url_count + 1 > 100 THEN
        INSERT INTO stop_words(word) VALUES (NEW.words_word)
            ON CONFLICT (word) DO NOTHING;
        DELETE FROM words WHERE word = NEW.words_word;
        DELETE FROM words_url WHERE words_word = NEW.words_word;
        RETURN NULL; 
    END IF;

    RETURN NEW; 
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER stop_word_trigger
BEFORE INSERT ON words_url
FOR EACH ROW
EXECUTE FUNCTION check_stop_word();