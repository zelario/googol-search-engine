DO $$
DECLARE
    current_docs BIGINT;
    df_threshold DOUBLE PRECISION;
    target_percentile FLOAT := 0.02;  -- Percentagem de teste
BEGIN
    -- conta documentos atuais
    SELECT COUNT(*) INTO current_docs FROM url;

    -- calcula threshold baseado no tamanho atual do índice
    df_threshold := current_docs * target_percentile;

    RAISE NOTICE 'Total docs = %, DF threshold (%.%) = %', 
        current_docs, target_percentile, target_percentile * 100, df_threshold;

    -- lista palavras candidatas a stop words
    RAISE NOTICE 'Palavras candidatas a stop_words: %',
        (
            SELECT string_agg(words_word || ' (DF=' || df || ')', ', ')
            FROM (
                SELECT wu.words_word, COUNT(DISTINCT wu.url_url) AS df
                FROM words_url wu
                GROUP BY wu.words_word
                HAVING COUNT(DISTINCT wu.url_url) >= df_threshold
            ) sub
        );
END $$;
