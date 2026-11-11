-- 목록 기본순(sort=MIXED)에 쓰는 정렬 키.
-- 같은 날 같은 곳에서 함께 구조된 형제견은 공고일이 같고 id 가 연달아 붙어 있어, 공고일순으로는 목록에 줄줄이 붙어 나온다.
-- 개마다 무작위 값을 저장해 두고 이 값으로 정렬하면 고르게 섞이고, 값이 저장돼 있어 페이지를 넘겨도 순서가 바뀌지 않는다
-- (ORDER BY random() 은 페이지를 부를 때마다 순서가 달라져 같은 개가 두 번 나오거나 빠진다).
-- 기본값이 휘발성 함수라 기존 행도 행마다 다른 값으로 채워지고, 새로 넣는 행도 DB 기본값으로 정해진다.
-- 동기화가 끝날 때마다 목록에 있는 개의 값을 다시 섞는다 (PetRepository#reshuffleSortKeys).
ALTER TABLE pet ADD COLUMN sort_key INTEGER NOT NULL DEFAULT floor(random() * 2147483647)::integer;
