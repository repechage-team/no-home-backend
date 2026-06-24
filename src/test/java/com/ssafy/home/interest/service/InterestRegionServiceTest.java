package com.ssafy.home.interest.service;

import com.ssafy.home.common.region.SeoulLawdCodeResolver;
import com.ssafy.home.interest.dto.InterestRegion;
import com.ssafy.home.interest.dto.InterestRegionRequest;
import com.ssafy.home.interest.dto.InterestRegionResponse;
import com.ssafy.home.interest.mapper.InterestRegionMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterestRegionServiceTest {

    @Test
    void findMyRegionsRequiresLogin() {
        InterestRegionService service = new InterestRegionService(new StubInterestRegionMapper(), new SeoulLawdCodeResolver());

        assertThatThrownBy(() -> service.findMyRegions(null))
                .isInstanceOf(InterestRegionException.class)
                .hasMessage("login required.");
    }

    @Test
    void addMyRegionValidatesRegionAndCreatesInterest() {
        StubInterestRegionMapper mapper = new StubInterestRegionMapper();
        InterestRegionService service = new InterestRegionService(mapper, new SeoulLawdCodeResolver());

        InterestRegionResponse response = service.addMyRegion(1L,
                new InterestRegionRequest("11590", "서울특별시", "동작구", "흑석동"));

        assertThat(response.lawdCd()).isEqualTo("11590");
        assertThat(response.sigungu()).isEqualTo("동작구");
        assertThat(response.umdNm()).isEqualTo("흑석동");
        assertThat(mapper.regions).hasSize(1);
        assertThat(mapper.interests).hasSize(1);
    }

    @Test
    void addMyRegionRejectsUnsupportedDong() {
        InterestRegionService service = new InterestRegionService(new StubInterestRegionMapper(), new SeoulLawdCodeResolver());

        assertThatThrownBy(() -> service.addMyRegion(1L,
                new InterestRegionRequest("11590", "서울특별시", "동작구", "없는동")))
                .isInstanceOf(InterestRegionException.class)
                .hasMessage("unsupported interest region.");
    }

    @Test
    void deleteMyRegionDeletesOnlyRequesterOwnedRegion() {
        StubInterestRegionMapper mapper = new StubInterestRegionMapper();
        InterestRegionService service = new InterestRegionService(mapper, new SeoulLawdCodeResolver());
        InterestRegionResponse response = service.addMyRegion(1L,
                new InterestRegionRequest("11590", "서울특별시", "동작구", "흑석동"));

        service.deleteMyRegion(1L, response.interestRegionId());

        assertThat(mapper.interests).isEmpty();
    }

    private static final class StubInterestRegionMapper implements InterestRegionMapper {
        private long regionSeq = 1L;
        private long interestSeq = 1L;
        private final List<RegionRow> regions = new ArrayList<>();
        private final List<InterestRow> interests = new ArrayList<>();

        @Override
        public List<InterestRegion> selectByMemberId(Long memberId) {
            return interests.stream()
                    .filter(interest -> memberId.equals(interest.memberId))
                    .flatMap(interest -> regions.stream()
                            .filter(region -> region.regionId.equals(interest.regionId))
                            .map(region -> new InterestRegion(
                                    interest.interestRegionId,
                                    interest.memberId,
                                    region.regionId,
                                    region.lawdCd,
                                    region.legalDongCode,
                                    region.sido,
                                    region.sigungu,
                                    region.umdNm,
                                    LocalDateTime.of(2026, 6, 23, 10, 0)
                            )))
                    .toList();
        }

        @Override
        public Optional<Long> selectRegionId(String lawdCd, String umdNm) {
            return regions.stream()
                    .filter(region -> region.lawdCd.equals(lawdCd) && region.umdNm.equals(umdNm))
                    .map(region -> region.regionId)
                    .findFirst();
        }

        @Override
        public int insertRegion(String lawdCd, String legalDongCode, String sido, String sigungu, String umdNm) {
            if (selectRegionId(lawdCd, umdNm).isPresent()) {
                return 0;
            }
            regions.add(new RegionRow(regionSeq++, lawdCd, legalDongCode, sido, sigungu, umdNm));
            return 1;
        }

        @Override
        public int insertInterestRegion(Long memberId, Long regionId) {
            boolean exists = interests.stream()
                    .anyMatch(interest -> interest.memberId.equals(memberId) && interest.regionId.equals(regionId));
            if (exists) {
                return 0;
            }
            interests.add(new InterestRow(interestSeq++, memberId, regionId));
            return 1;
        }

        @Override
        public int deleteInterestRegion(Long memberId, Long interestRegionId) {
            return interests.removeIf(interest ->
                    interest.memberId.equals(memberId) && interest.interestRegionId.equals(interestRegionId)
            ) ? 1 : 0;
        }
    }

    private record RegionRow(
            Long regionId,
            String lawdCd,
            String legalDongCode,
            String sido,
            String sigungu,
            String umdNm
    ) {
    }

    private record InterestRow(Long interestRegionId, Long memberId, Long regionId) {
    }
}
