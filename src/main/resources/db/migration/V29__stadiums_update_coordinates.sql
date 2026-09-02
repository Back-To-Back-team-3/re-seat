-- V29__stadiums_update_coordinates.sql
-- 구장 좌표 기준 데이터 보완 (T3-14)

UPDATE stadiums
SET latitude  = 37.5121676,
    longitude = 127.0719084
WHERE name = '서울종합운동장 야구장';
UPDATE stadiums
SET latitude  = 37.4982167,
    longitude = 126.8670890
WHERE name = '고척스카이돔';
UPDATE stadiums
SET latitude  = 37.4370190,
    longitude = 126.6932817
WHERE name = '인천SSG랜더스필드';
UPDATE stadiums
SET latitude  = 37.2997397,
    longitude = 127.0097732
WHERE name = '수원KT위즈파크';
UPDATE stadiums
SET latitude  = 35.8410817,
    longitude = 128.6816522
WHERE name = '대구삼성라이온즈파크';
UPDATE stadiums
SET latitude  = 35.2228254,
    longitude = 128.5820113
WHERE name = '창원NC파크';
UPDATE stadiums
SET latitude  = 35.1940422,
    longitude = 129.0615501
WHERE name = '사직야구장';
UPDATE stadiums
SET latitude  = 35.1682340,
    longitude = 126.8891175
WHERE name = '광주-기아 챔피언스필드';
UPDATE stadiums
SET latitude  = 36.3161775,
    longitude = 127.4315351
WHERE name = '대전 한화생명 볼파크';