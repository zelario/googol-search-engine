DO $$
DECLARE
    total_docs BIGINT;
    doc_frequency DOUBLE PRECISION;
    target_percentile FLOAT := 0.95;  -- Percentil de teste
BEGIN
    SELECT COUNT(*) INTO total_docs FROM url;

    SELECT percentile_cont(target_percentile) WITHIN GROUP (ORDER BY df)
    INTO doc_frequency
    FROM (
        SELECT COUNT(DISTINCT wu.url_url) AS df
        FROM words_url wu
        GROUP BY wu.words_word
    ) sub;

    RAISE NOTICE 'Total docs = %, Doc frequency limite = %', total_docs, doc_frequency;

    RAISE NOTICE 'Palavras candidatas a stop_words: %',
        (SELECT string_agg(words_word || ' (DF=' || df || ')', ', ')
         FROM (
             SELECT wu.words_word, COUNT(DISTINCT wu.url_url) AS df
             FROM words_url wu
             GROUP BY wu.words_word
             HAVING COUNT(DISTINCT wu.url_url) >= doc_frequency
         ) sub);
END $$;
