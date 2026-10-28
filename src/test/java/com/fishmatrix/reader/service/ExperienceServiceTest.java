package com.fishmatrix.reader.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.reader.model.BookSummary;
import com.fishmatrix.reader.model.UserStats;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ExperienceServiceTest {

    private static final String SAMPLE_TEXT = "第一章 开始\n\n正文内容。\n\n第二章 继续\n\n更多内容。";

    /** 心跳间隔封顶 5 分钟，所以每次推进 5 分钟，12 次正好 1 小时。 */
    private static final long FIVE_MINUTES = 5 * 60L;

    @Autowired
    private ExperienceService experienceService;

    @Autowired
    private LibraryService libraryService;

    private final CurrentUser reader = user(21, "读者", "reader.public.upload");
    private final CurrentUser author = user(22, "作者", "reader.public.upload");

    @Test
    @DisplayName("等级：9 起步，每 10 点降一级，满级封顶 0")
    void levelMath() {
        assertThat(ExperienceService.levelOf(0)).isEqualTo(9);
        assertThat(ExperienceService.levelOf(9)).isEqualTo(9);
        assertThat(ExperienceService.levelOf(10)).isEqualTo(8);
        assertThat(ExperienceService.levelOf(89)).isEqualTo(1);
        assertThat(ExperienceService.levelOf(90)).isEqualTo(0);
        assertThat(ExperienceService.levelOf(500)).isEqualTo(0);
    }

    @Test
    @DisplayName("上传经验累计上限 20 点，但上传本数继续累加")
    void uploadExperienceIsCappedWhileCountKeepsGrowing() {
        for (int i = 0; i < 25; i++) {
            experienceService.rewardUpload(author.id());
        }

        UserStats stats = experienceService.stats(author.id());

        assertThat(stats.experience()).isEqualTo(ExperienceService.UPLOAD_EXP_CAP);
        assertThat(stats.uploadCount()).isEqualTo(25);
        assertThat(stats.level()).isEqualTo(7);
        assertThat(stats.nextLevelExperience()).isEqualTo(30);
    }

    @Test
    @DisplayName("阅读时长按两次心跳的间隔累计，每满 20 分钟 +1 经验")
    void heartbeatAccumulatesReadingTime() {
        BookSummary book = importPublicBook(author);
        Instant start = Instant.parse("2026-10-27T10:00:00Z");

        experienceService.heartbeatAt(reader.id(), book.id(), start);
        // 首次心跳只记录起点，不产生时长
        assertThat(experienceService.stats(reader.id()).readingMinutes()).isZero();

        for (int i = 1; i <= 4; i++) {
            experienceService.heartbeatAt(reader.id(), book.id(), start.plusSeconds(FIVE_MINUTES * i));
        }

        UserStats stats = experienceService.stats(reader.id());
        assertThat(stats.readingMinutes()).isEqualTo(20);
        assertThat(stats.experience()).isEqualTo(1);
    }

    @Test
    @DisplayName("单次心跳间隔封顶 5 分钟，挂机一小时也只算 5 分钟")
    void heartbeatGapIsCapped() {
        BookSummary book = importPublicBook(author);
        Instant start = Instant.parse("2026-10-27T10:00:00Z");

        experienceService.heartbeatAt(reader.id(), book.id(), start);
        experienceService.heartbeatAt(reader.id(), book.id(), start.plusSeconds(3600));

        assertThat(experienceService.stats(reader.id()).readingMinutes())
                .isEqualTo(ExperienceService.MAX_HEARTBEAT_GAP_MINUTES);
    }

    @Test
    @DisplayName("自己的书被别人读满 1 小时，书主 +1；自己读自己的书不结算书主经验")
    void ownerRewardedOncePerHourAndNeverForSelf() {
        BookSummary book = importPublicBook(author);
        Instant start = Instant.parse("2026-10-27T10:00:00Z");

        // 别人读满 1 小时：书主 +1（另有导入时的 +1 上传经验）
        experienceService.heartbeatAt(reader.id(), book.id(), start);
        for (int i = 1; i <= 12; i++) {
            experienceService.heartbeatAt(reader.id(), book.id(), start.plusSeconds(FIVE_MINUTES * i));
        }
        int afterOtherReads = experienceService.stats(author.id()).experience();
        assertThat(afterOtherReads).as("上传 1 点 + 被读 1 点").isEqualTo(2);

        // 自己读自己的书 1 小时：只涨阅读经验（60 分钟 / 20 = 3 点），不结算书主经验
        Instant later = start.plusSeconds(FIVE_MINUTES * 12);
        experienceService.heartbeatAt(author.id(), book.id(), later);
        for (int i = 1; i <= 12; i++) {
            experienceService.heartbeatAt(author.id(), book.id(), later.plusSeconds(FIVE_MINUTES * i));
        }

        assertThat(experienceService.stats(author.id()).experience())
                .as("自读不给书主经验，只增加 3 点阅读经验")
                .isEqualTo(afterOtherReads + 3);
    }

    @Test
    @DisplayName("导入一本书会给上传者 +1 经验")
    void importGrantsUploadExperience() {
        int before = experienceService.stats(author.id()).experience();
        importPublicBook(author);
        assertThat(experienceService.stats(author.id()).experience()).isEqualTo(before + 1);
    }

    private BookSummary importPublicBook(CurrentUser who) {
        MockMultipartFile file =
                new MockMultipartFile("file", "book.txt", "text/plain", SAMPLE_TEXT.getBytes(StandardCharsets.UTF_8));
        return libraryService.importBook("public", "经验值测试书", "作者", null, file, who);
    }

    private static CurrentUser user(long id, String nickname, String... permissions) {
        return new CurrentUser(id, "u" + id, nickname, "USER", List.of("USER"), Set.of(permissions));
    }
}
