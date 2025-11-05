DROP TRIGGER update_doc_frequency ON words_url;

CREATE OR REPLACE FUNCTION update_doc_frequency()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE words
    SET doc_freq = doc_freq + 1
    WHERE word = NEW.words_word;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_doc_frequency
AFTER INSERT ON words_url
FOR EACH ROW
EXECUTE FUNCTION update_doc_frequency();