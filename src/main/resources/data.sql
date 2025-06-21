INSERT INTO tier (tier_id, tier_name, character_url, required_exp)
SELECT UNHEX(REPLACE(UUID(), '-', '')), '바보 감자', 'https://commitato.s3.ap-northeast-2.amazonaws.com/stupidPotato.svg', 0
WHERE NOT EXISTS (
    SELECT 1 FROM tier WHERE tier_name = '바보 감자'
);

INSERT INTO tier (tier_id, tier_name, character_url, required_exp)
SELECT UNHEX(REPLACE(UUID(), '-', '')), '말하는 감자', 'https://commitato.s3.ap-northeast-2.amazonaws.com/speakingPotato.svg', 30000
WHERE NOT EXISTS (
    SELECT 1 FROM tier WHERE tier_name = '말하는 감자'
);

INSERT INTO tier (tier_id, tier_name, character_url, required_exp)
SELECT UNHEX(REPLACE(UUID(), '-', '')), '개발자 감자', 'https://commitato.s3.ap-northeast-2.amazonaws.com/developerPotato.svg', 150000
WHERE NOT EXISTS (
    SELECT 1 FROM tier WHERE tier_name = '개발자 감자'
);

INSERT INTO tier (tier_id, tier_name, character_url, required_exp)
SELECT UNHEX(REPLACE(UUID(), '-', '')), 'CEO 감자', 'https://commitato.s3.ap-northeast-2.amazonaws.com/CEOPotato.svg', 300000
WHERE NOT EXISTS (
    SELECT 1 FROM tier WHERE tier_name = 'CEO 감자'
);
