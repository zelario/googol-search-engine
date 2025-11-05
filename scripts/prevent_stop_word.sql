DROP TRIGGER IF EXISTS prevent_stop_word ON words;

CREATE OR REPLACE FUNCTION prevent_stop_word()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM stop_words WHERE word = NEW.word) THEN
        RETURN NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER prevent_stop_word
BEFORE INSERT ON words
FOR EACH ROW
EXECUTE FUNCTION prevent_stop_word();