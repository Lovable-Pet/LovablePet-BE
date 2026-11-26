-- 상세 화면 카드를 뒤집자마자 보여 줄 미리보기 그림 (1200x930 투명 배경 WebP).
-- model-dir 기준 상대 경로: {유기번호}/{job_id}-preview-{내용 해시 8자}.webp
-- 모델이 완성된 뒤 서버가 따로 찍어 채우므로, 찍기 전이거나 찍지 못한 모델은 비어 있다 (앱은 GLB 를 직접 그린다)
ALTER TABLE pet_model ADD COLUMN preview_path TEXT;
