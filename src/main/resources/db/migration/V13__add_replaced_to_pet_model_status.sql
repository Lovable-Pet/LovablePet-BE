-- 완성 모델이 있는 개도 다시 만들 수 있다. 새 모델이 완성되면 옛 완성 모델은 REPLACED 가 되고 파일(GLB·USDZ·미리보기)은 지운다.
-- 완성(DONE)은 여전히 동물마다 하나뿐이다 (uk_pet_model_done 그대로). 새 모델이 완성되기 전까지는 옛 모델을 계속 보여 준다.
-- REPLACED 는 DB 안에서만 쓴다. 관리자 API 에는 status=DONE, replaced=true 로 나간다.
ALTER TABLE pet_model DROP CONSTRAINT pet_model_status_check;
ALTER TABLE pet_model ADD CONSTRAINT pet_model_status_check
    CHECK (status IN ('PENDING', 'DONE', 'PHOTO_REJECTED', 'UNSUITABLE', 'FAILED', 'REPLACED'));
