package com.lovablepet.domain.pet.service;

import com.lovablepet.domain.pet.client.PublicAnimalItem;
import com.lovablepet.domain.pet.entity.Pet;
import com.lovablepet.domain.pet.repository.PetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PetSyncWriterTest {

    private static final Instant SYNCED_AT = Instant.parse("2026-10-29T03:00:00Z");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private PetRepository petRepository;

    @Test
    @DisplayName("없는 동물은 새로 넣고, 있는 동물은 덮어쓰고, 유기번호가 없으면 건너뛴다")
    void upsert() {
        Pet existing = Pet.from(item("100", "보호중", "5.0(Kg)"), SYNCED_AT.minus(1, ChronoUnit.DAYS));
        given(petRepository.findAllByDesertionNoIn(anyCollection())).willReturn(List.of(existing));
        PetSyncWriter writer = new PetSyncWriter(petRepository);

        PetSyncWriter.UpsertCount count = writer.upsert(List.of(
                item("100", "종료(입양)", "6.0(Kg)"),
                item("200", "보호중", "3.0(Kg)"),
                item("200", "보호중", "3.5(Kg)"), // 같은 페이지에 두 번 → 한 번만 넣는다
                item(null, "보호중", "1.0(Kg)")
        ), SYNCED_AT);

        assertThat(count).isEqualTo(new PetSyncWriter.UpsertCount(1, 1, 1));

        assertThat(existing.getProcessState()).isEqualTo("종료(입양)");
        assertThat(existing.isListed()).isFalse();
        assertThat(existing.getWeightKg()).isEqualByComparingTo("6");
        assertThat(existing.getSyncedAt()).isEqualTo(SYNCED_AT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Pet>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(petRepository).saveAll(captor.capture());
        List<Pet> created = new ArrayList<>();
        captor.getValue().forEach(created::add);
        assertThat(created).singleElement().satisfies(pet -> {
            assertThat(pet.getDesertionNo()).isEqualTo("200");
            assertThat(pet.getWeightKg()).isEqualByComparingTo("3.5");
            assertThat(pet.isListed()).isTrue();
            assertThat(pet.getRaw()).contains("\"desertionNo\":\"200\"");
        });
    }

    private PublicAnimalItem item(String desertionNo, String processState, String weight) {
        String no = desertionNo == null ? "" : "\"desertionNo\":\"" + desertionNo + "\",";
        return new PublicAnimalItem(jsonMapper.readTree(
                "{" + no + "\"processState\":\"" + processState + "\",\"weight\":\"" + weight + "\"}"));
    }
}
