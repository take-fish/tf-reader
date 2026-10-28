package com.fishmatrix.reader.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fishmatrix.reader.entity.ReaderBook;
import com.fishmatrix.reader.entity.ReaderReadingStat;
import com.fishmatrix.reader.entity.ReaderUserStats;
import com.fishmatrix.reader.mapper.ReaderBookMapper;
import com.fishmatrix.reader.mapper.ReaderReadingStatMapper;
import com.fishmatrix.reader.mapper.ReaderUserStatsMapper;
import com.fishmatrix.reader.model.UserStats;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 经验值与等级。
 *
 * <p>规则：上传 +1（累计上限 20 点）；阅读每满 20 分钟 +1；自己的书被别人读每满 1 小时 +1。
 * 等级 9→0，0 最高，{@code level = max(0, 9 - experience / 10)}，满级后经验继续累计。</p>
 *
 * <p>阅读时长<b>不接受前端自报</b>：由前端每分钟发心跳，服务端用两次心跳的时间差累计，
 * 且单次间隔封顶 5 分钟，避免挂机或伪造超长间隔。</p>
 */
@Service
public class ExperienceService {

    public static final int MAX_LEVEL = 9;
    public static final int EXP_PER_LEVEL = 10;
    /** 上传经验一辈子累计的上限。 */
    public static final int UPLOAD_EXP_CAP = 20;
    public static final int MINUTES_PER_READING_EXP = 20;
    public static final int OWNER_REWARD_MINUTES = 60;
    public static final int MAX_HEARTBEAT_GAP_MINUTES = 5;

    private final ReaderUserStatsMapper statsMapper;
    private final ReaderReadingStatMapper readingMapper;
    private final ReaderBookMapper bookMapper;

    public ExperienceService(
            ReaderUserStatsMapper statsMapper,
            ReaderReadingStatMapper readingMapper,
            ReaderBookMapper bookMapper) {
        this.statsMapper = statsMapper;
        this.readingMapper = readingMapper;
        this.bookMapper = bookMapper;
    }

    /** 等级 9→0，0 最高。满级后继续返回 0。 */
    public static int levelOf(long experience) {
        int level = MAX_LEVEL - (int) (Math.max(0, experience) / EXP_PER_LEVEL);
        return Math.max(0, level);
    }

    /** 达到某一级所需的累计经验。 */
    public static int experienceForLevel(int level) {
        return (MAX_LEVEL - level) * EXP_PER_LEVEL;
    }

    /** 上传一本书：+1 经验，但一辈子最多给 20 点；上传本数本身一直累加。 */
    @Transactional
    public void rewardUpload(long userId) {
        ReaderUserStats stats = getOrCreate(userId);
        int uploadCount = stats.getUploadCount() + 1;
        int gained = uploadCount <= UPLOAD_EXP_CAP ? 1 : 0;
        stats.setUploadCount(uploadCount);
        stats.setExperience(stats.getExperience() + gained);
        stats.setLevel(levelOf(stats.getExperience()));
        statsMapper.updateById(stats);
    }

    @Transactional
    public UserStats heartbeat(long userId, long bookId) {
        return heartbeatAt(userId, bookId, Instant.now());
    }

    /** 包级可见，便于测试用固定时间戳验证时长累计。 */
    @Transactional
    UserStats heartbeatAt(long userId, long bookId, Instant now) {
        ReaderReadingStat stat = readingMapper.selectOne(Wrappers.<ReaderReadingStat>lambdaQuery()
                .eq(ReaderReadingStat::getUserId, userId)
                .eq(ReaderReadingStat::getBookId, bookId));

        int oldMinutes = stat == null ? 0 : stat.getMinutes();
        int addedMinutes = 0;
        if (stat != null && stat.getLastHeartbeatAt() != null) {
            long seconds = Duration.between(stat.getLastHeartbeatAt(), now).getSeconds();
            // 单次间隔封顶，避免"挂机一整天"变成真实阅读时长
            addedMinutes = (int) Math.min(MAX_HEARTBEAT_GAP_MINUTES, Math.max(0, seconds) / 60);
        }
        int newMinutes = oldMinutes + addedMinutes;

        int readingGain = newMinutes / MINUTES_PER_READING_EXP
                - oldMinutes / MINUTES_PER_READING_EXP;

        int rewardedBefore = stat == null ? 0 : stat.getRewardedHours();
        int rewardedAfter = newMinutes / OWNER_REWARD_MINUTES;
        int ownerGain = rewardedAfter - rewardedBefore;

        if (stat == null) {
            ReaderReadingStat created = new ReaderReadingStat();
            created.setUserId(userId);
            created.setBookId(bookId);
            created.setMinutes(newMinutes);
            created.setRewardedHours(rewardedAfter);
            created.setLastHeartbeatAt(now);
            readingMapper.insert(created);
        } else {
            stat.setMinutes(newMinutes);
            stat.setRewardedHours(rewardedAfter);
            stat.setLastHeartbeatAt(now);
            readingMapper.update(stat, Wrappers.<ReaderReadingStat>lambdaUpdate()
                    .eq(ReaderReadingStat::getUserId, userId)
                    .eq(ReaderReadingStat::getBookId, bookId));
        }

        ReaderUserStats stats = getOrCreate(userId);
        stats.setReadingMinutes(stats.getReadingMinutes() + addedMinutes);
        stats.setExperience(stats.getExperience() + readingGain);
        stats.setLevel(levelOf(stats.getExperience()));
        statsMapper.updateById(stats);

        // 别人在读我的书：每满 1 小时给书主 1 点；自己读自己的书不结算。
        if (ownerGain > 0) {
            ReaderBook book = bookMapper.selectByIdWithCategory(bookId);
            if (book != null && book.getOwnerId() != null && !book.getOwnerId().equals(userId)) {
                addExperience(book.getOwnerId(), ownerGain);
            }
        }
        return toView(stats);
    }

    public UserStats stats(long userId) {
        ReaderUserStats stats = statsMapper.selectById(userId);
        if (stats == null) {
            return new UserStats(0, MAX_LEVEL, experienceForLevel(MAX_LEVEL - 1), 0, 0);
        }
        return toView(stats);
    }

    private void addExperience(long userId, int gained) {
        ReaderUserStats stats = getOrCreate(userId);
        stats.setExperience(stats.getExperience() + gained);
        stats.setLevel(levelOf(stats.getExperience()));
        statsMapper.updateById(stats);
    }

    private ReaderUserStats getOrCreate(long userId) {
        ReaderUserStats stats = statsMapper.selectById(userId);
        if (stats != null) {
            return stats;
        }
        ReaderUserStats created = new ReaderUserStats();
        created.setUserId(userId);
        created.setExperience(0);
        created.setLevel(MAX_LEVEL);
        created.setUploadCount(0);
        created.setReadingMinutes(0L);
        statsMapper.insert(created);
        return created;
    }

    private static UserStats toView(ReaderUserStats stats) {
        int level = levelOf(stats.getExperience());
        Integer nextLevelExperience = level == 0 ? null : experienceForLevel(level - 1);
        return new UserStats(
                stats.getExperience(),
                level,
                nextLevelExperience,
                stats.getUploadCount(),
                stats.getReadingMinutes());
    }
}
