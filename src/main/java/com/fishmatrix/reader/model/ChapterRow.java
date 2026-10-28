package com.fishmatrix.reader.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 待写入的章节行（批量 insert 用）。用普通类而不是 record，方便 MyBatis 通过 getter 取值。 */
@Data
@NoArgsConstructor
public class ChapterRow {

    private int index;
    private String title;
    private String content;
    private int wordCount;

    public ChapterRow(int index, String title, String content, int wordCount) {
        this.index = index;
        this.title = title;
        this.content = content;
        this.wordCount = wordCount;
    }
}
