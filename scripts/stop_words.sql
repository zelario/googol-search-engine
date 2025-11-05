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

DROP PROCEDURE IF EXISTS check_stop_words(FLOAT);

CREATE OR REPLACE PROCEDURE check_stop_words(threshold FLOAT)
LANGUAGE plpgsql
AS $$
DECLARE
    total_docs BIGINT;
BEGIN
    SELECT COUNT(*) INTO total_docs FROM url;

    WITH word_stats AS (
        SELECT
            wu.words_word AS word,
            COUNT(DISTINCT wu.url_url) AS doc_frequency
        FROM words_url wu
        GROUP BY wu.words_word
    )
    INSERT INTO stop_words(word)
    SELECT ws.word
    FROM word_stats ws
    WHERE LN(total_docs::FLOAT / NULLIF(ws.doc_frequency, 0)::FLOAT) < threshold
    ON CONFLICT (word) DO NOTHING;
END;
$$;
