DROP PROCEDURE IF EXISTS check_stop_words(FLOAT);

CREATE OR REPLACE PROCEDURE check_stop_words(percentile FLOAT)
LANGUAGE plpgsql
AS $$
DECLARE
    current_docs BIGINT;
    df_threshold INT;
BEGIN
    -- conta documentos atuais
    SELECT COUNT(*) INTO current_docs FROM url;

    -- calcula threshold baseado no tamanho atual do índice e arredonda para inteiro
    df_threshold := CEIL(current_docs * percentile);

    -- insere stop words
    INSERT INTO stop_words(word)
    SELECT wu.words_word
    FROM words_url wu
    GROUP BY wu.words_word
    HAVING COUNT(DISTINCT wu.url_url) >= df_threshold
    ON CONFLICT (word) DO NOTHING;

    -- remove das words_url
    DELETE FROM words_url wu
    USING stop_words sw
    WHERE wu.words_word = sw.word;

    -- remove das words
    DELETE FROM words w
    USING stop_words sw
    WHERE w.word = sw.word;

END;
$$;
