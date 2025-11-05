CREATE OR REPLACE PROCEDURE check_stop_words(threshold FLOAT)
LANGUAGE plpgsql
AS $$
DECLARE
    total_docs BIGINT;
BEGIN
    SELECT COUNT(*) INTO total_docs FROM url;

    INSERT INTO stop_words(word)
    SELECT w.word
    FROM words w
    WHERE LN(total_docs::FLOAT / NULLIF(w.doc_freq, 0)::FLOAT) < threshold
    ON CONFLICT (word) DO NOTHING;
END;
$$;
