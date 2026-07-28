package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neobank.module.dto.CreateProductVersionRequest;
import com.neobank.module.dto.ProductVersionCreated;
import com.neobank.module.dto.ProductVersionView;
import com.neobank.module.model.ProductConfig;
import com.neobank.module.repository.ProductConfigRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProductConfigServiceTest {

    @Autowired
    private ProductConfigService service;

    @Autowired
    private ProductConfigRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void createsNewVersionWithIncrementedVersionNumber() {
        repository.save(new ProductConfig("TEST_PRODUCT", 1, 18, 1000, 10000, true, "WEB", Instant.now()));

        ProductVersionCreated created = service.createVersion(request("TEST_PRODUCT", 18, 1000, 10000, true, List.of("WEB")));
        assertThat(created.version()).isEqualTo(2);

        ProductVersionCreated createdAgain = service.createVersion(request("TEST_PRODUCT", 19, 2000, 15000, true, List.of("WEB", "MOBILE_APP")));
        assertThat(createdAgain.version()).isEqualTo(3);
    }

    @Test
    void returnsAllVersionsOrderedOldestFirst() {
        repository.save(new ProductConfig("TEST_PRODUCT", 1, 18, 500, 5000, true, "WEB", Instant.now()));
        service.createVersion(request("TEST_PRODUCT", 19, 600, 6000, true, List.of("WEB")));

        List<ProductVersionView> versions = service.getVersions("TEST_PRODUCT");
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).version()).isEqualTo(1);
        assertThat(versions.get(1).version()).isEqualTo(2);
        assertThat(versions.get(0).current()).isFalse();
        assertThat(versions.get(1).current()).isTrue();
    }

    @Test
    void returnsCreditCardRewardsVersionHistoryWithCurrentCheckpoint() {
        repository.save(new ProductConfig(
                "CREDIT_CARD_REWARDS", 1, 18, 1000, 10000, true, "WEB,MOBILE_APP,BRANCH",
                Instant.parse("2026-07-01T00:00:00Z")));

        service.createVersion(request("CREDIT_CARD_REWARDS", 18, 1200, 11000, true,
                List.of("WEB", "MOBILE_APP", "BRANCH")));
        service.createVersion(request("CREDIT_CARD_REWARDS", 19, 1500, 12000, true,
                List.of("WEB", "MOBILE_APP", "BRANCH", "PHONE")));
        service.createVersion(request("CREDIT_CARD_REWARDS", 20, 2000, 15000, false,
                List.of("WEB", "BRANCH")));

        List<ProductVersionView> versions = service.getVersions("CREDIT_CARD_REWARDS");

        assertThat(versions).hasSize(4);
        assertThat(versions).extracting(ProductVersionView::version).containsExactly(1, 2, 3, 4);
        assertThat(versions).extracting(ProductVersionView::current).containsExactly(false, false, false, true);
        assertThat(versions.getFirst().minAge()).isEqualTo(18);
        assertThat(versions.getFirst().limitMin()).isEqualTo(1000);
        assertThat(versions.getFirst().limitMax()).isEqualTo(10000);
        assertThat(versions.getFirst().active()).isTrue();
        assertThat(versions.getFirst().channels()).containsExactly("WEB", "MOBILE_APP", "BRANCH");
        assertThat(versions.get(3).active()).isFalse();
        assertThat(versions.get(3).channels()).containsExactly("WEB", "BRANCH");
    }

    @Test
    void returnsAllProductCodes() {
        repository.save(new ProductConfig("PRODUCT_A", 1, 18, 500, 5000, true, "WEB", Instant.now()));
        repository.save(new ProductConfig("PRODUCT_B", 1, 21, 1000, 10000, true, "MOBILE_APP", Instant.now()));

        List<String> codes = service.getAllProductCodes();
        assertThat(codes).containsExactly("PRODUCT_A", "PRODUCT_B");
    }

    @Test
    void rejectsWhenLimitMinGreaterThanOrEqualToLimitMax() {
        assertThatThrownBy(() -> service.createVersion(
                request("TEST_PRODUCT", 18, 5000, 5000, true, List.of("WEB"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limitMin must be less than limitMax");

        assertThatThrownBy(() -> service.createVersion(
                request("TEST_PRODUCT", 18, 6000, 5000, true, List.of("WEB"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limitMin must be less than limitMax");
    }

    @Test
    void rejectsUnknownChannel() {
        assertThatThrownBy(() -> service.createVersion(
                request("TEST_PRODUCT", 18, 1000, 10000, true, List.of("WEB", "UNKNOWN"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown channel: UNKNOWN");
    }

    @Test
    void rejectsEmptyChannels() {
        assertThatThrownBy(() -> service.createVersion(
                new CreateProductVersionRequest("TEST_PRODUCT", 18, 1000, 10000, true, List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("channels must not be empty");
    }

    @Test
    void rejectsUnknownProductCodeForGetVersions() {
        assertThatThrownBy(() -> service.getVersions("UNKNOWN_PRODUCT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown productCode: UNKNOWN_PRODUCT");
    }

    @Test
    void seedDataExistsOnFirstBoot() {
        repository.deleteAll();
        repository.save(new ProductConfig("SEED_TEST", 1, 18, 1000, 10000, true, "WEB,MOBILE_APP,BRANCH", Instant.parse("2026-07-01T00:00:00Z")));

        List<ProductVersionView> versions = service.getVersions("SEED_TEST");
        assertThat(versions).hasSize(1);
        assertThat(versions.getFirst().version()).isEqualTo(1);
        assertThat(versions.getFirst().minAge()).isEqualTo(18);
        assertThat(versions.getFirst().limitMin()).isEqualTo(1000);
        assertThat(versions.getFirst().limitMax()).isEqualTo(10000);
        assertThat(versions.getFirst().active()).isTrue();
        assertThat(versions.getFirst().channels()).containsExactly("WEB", "MOBILE_APP", "BRANCH");
        assertThat(versions.getFirst().current()).isTrue();
    }

    private static CreateProductVersionRequest request(String productCode, int minAge, int limitMin, int limitMax, boolean active, List<String> channels) {
        return new CreateProductVersionRequest(productCode, minAge, limitMin, limitMax, active, channels);
    }
}