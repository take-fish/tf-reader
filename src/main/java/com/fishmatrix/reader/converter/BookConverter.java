package com.fishmatrix.reader.converter;

import com.fishmatrix.reader.entity.ReaderBook;
import com.fishmatrix.reader.model.BookSummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookConverter {

    /**
     * mine / canEdit / canDelete 由服务层按当前用户判定后传入，
     * 与重构前 LibraryService.summary(...) 的计算口径保持一致。
     */
    @Mapping(target = "mine", source = "mine")
    @Mapping(target = "canEdit", source = "canEdit")
    @Mapping(target = "canDelete", source = "canDelete")
    BookSummary toSummary(ReaderBook book, boolean mine, boolean canEdit, boolean canDelete);
}
