-- 아이폰 AR(Quick Look)용 USDZ. model-dir 기준 상대 경로: {유기번호}/{job_id}.usdz
-- 파이프라인의 변환이 실패했거나 USDZ 를 받기 전에 완성된 모델은 비어 있다 (GLB 는 그대로 쓴다)
ALTER TABLE pet_model ADD COLUMN usdz_path TEXT;
