package com.lovablepet.domain.pet.service;

import com.lovablepet.domain.pet.client.PublicAnimalItem;
import com.lovablepet.domain.pet.entity.Pet;
import com.lovablepet.domain.pet.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 동기화한 한 페이지(최대 1000마리)를 한 트랜잭션으로 저장한다. 유기번호가 같으면 덮어쓴다.
 */
@Component
@RequiredArgsConstructor
public class PetSyncWriter {

    private final PetRepository petRepository;

    @Transactional
    public UpsertCount upsert(List<PublicAnimalItem> items, Instant syncedAt) {
        List<PublicAnimalItem> valid = items.stream().filter(item -> item.desertionNo() != null).toList();
        Map<String, Pet> existing = petRepository.findAllByDesertionNoIn(
                        valid.stream().map(PublicAnimalItem::desertionNo).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Pet::getDesertionNo, Function.identity()));

        // 한 페이지에 같은 동물이 두 번 와도 한 번만 넣는다 (유기번호 UNIQUE)
        Map<String, Pet> created = new LinkedHashMap<>();
        int updated = 0;
        for (PublicAnimalItem item : valid) {
            Pet pet = existing.get(item.desertionNo());
            if (pet != null) {
                pet.apply(item, syncedAt);
                updated++;
            } else if (created.containsKey(item.desertionNo())) {
                created.get(item.desertionNo()).apply(item, syncedAt);
            } else {
                created.put(item.desertionNo(), Pet.from(item, syncedAt));
            }
        }
        petRepository.saveAll(created.values());
        return new UpsertCount(created.size(), updated, items.size() - valid.size());
    }

    @Transactional
    public int unlistNotSyncedSince(Instant syncStartedAt) {
        return petRepository.unlistNotSyncedSince(syncStartedAt);
    }

    /** skipped: 유기번호가 없어 저장하지 못한 수 */
    public record UpsertCount(int created, int updated, int skipped) {
    }
}
