DROP PROCEDURE IF EXISTS check_stop_words(FLOAT);

CREATE OR REPLACE PROCEDURE check_stop_words(percentile FLOAT)
LANGUAGE plpgsql
AS $$
DECLARE
    total_docs BIGINT;
    doc_frequency DOUBLE PRECISION;
BEGIN
    -- conta paginas
    SELECT COUNT(*) INTO total_docs FROM url;

    -- calcula df correspondente ao percentil
    SELECT percentile_cont(percentile) WITHIN GROUP (ORDER BY df) 
    INTO doc_frequency
    FROM (
        SELECT COUNT(DISTINCT wu.url_url) AS df
        FROM words_url wu
        GROUP BY wu.words_word
    ) sub;

    -- insere palavras candidatas em stop_words
    INSERT INTO stop_words(word)
    SELECT wu.words_word
    FROM words_url wu
    GROUP BY wu.words_word
    HAVING COUNT(DISTINCT wu.url_url) >= doc_frequency
    ON CONFLICT (word) DO NOTHING;

    -- remove as stop_words de words_url
    DELETE FROM words_url wu
    USING stop_words sw
    WHERE wu.words_word = sw.word;

    -- remove as stop_words de words
    DELETE FROM words w
    USING stop_words sw
    WHERE w.word = sw.word;

END;
$$;
