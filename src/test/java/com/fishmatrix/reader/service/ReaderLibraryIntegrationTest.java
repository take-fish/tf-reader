package com.fishmatrix.reader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.reader.model.AccessRequestCreate;
import com.fishmatrix.reader.model.AccessRequestView;
import com.fishmatrix.reader.model.BookSummary;
import com.fishmatrix.reader.model.BookUpdateRequest;
import com.fishmatrix.reader.model.Category;
import com.fishmatrix.reader.model.CategoryRequest;
import com.fishmatrix.reader.model.NotFoundException;
import com.fishmatrix.reader.model.Shelf;
import com.fishmatrix.reader.model.ShelfDetail;
import com.fishmatrix.reader.model.ShelfRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ReaderLibraryIntegrationTest {

    private static final String SAMPLE_TEXT =
            "第一章 开始\n\n正文内容。\n\n第二章 继续\n\n更多内容。";

    @Autowired
    private LibraryService libraryService;

    @Autowired
    private AccessRequestService accessRequestService;

    @Autowired
    private ShelfService shelfService;

    @Autowired
    private CategoryService categoryService;

    private final CurrentUser owner = user(11, "书主",
            "reader.public.upload", "reader.private.manage");
    private final CurrentUser stranger = user(12, "路人");
    private final CurrentUser admin = user(13, "管理员",
            "reader.public.upload", "reader.private.manage",
            "reader.content.manage.any", "reader.category.manage");

    @Test
    void publicImportRequiresUploadPermission() {
        assertThatThrownBy(() -> importBook("public", stranger))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).status().value())
                .isEqualTo(403);

        BookSummary book = importBook("public", owner);
        assertThat(book.scope()).isEqualTo("PUBLIC");
        assertThat(book.ownerId()).isEqualTo(owner.id());
        assertThat(book.chapterCount()).isEqualTo(2);
        assertThat(book.mine()).isTrue();
    }

    @Test
    void privateBookIsOnlyReadableByOwnerAndApprovedUsers() {
        BookSummary book = importBook("private", owner);

        assertThat(libraryService.getBook(book.id(), owner).book().title())
                .isEqualTo(book.title());
        assertThatThrownBy(() -> libraryService.getBook(book.id(), stranger))
                .isInstanceOf(NotFoundException.class);
        // 管理员也不能读取私有库，权限隔离优先于管理能力。
        assertThatThrownBy(() -> libraryService.getBook(book.id(), admin))
                .isInstanceOf(NotFoundException.class);
        assertThat(libraryService.listMine(owner))
                .extracting(BookSummary::id).containsExactly(book.id());
        assertThat(libraryService.listMine(stranger)).isEmpty();
    }

    @Test
    void shareTokenOnlyAllowsApplyingUntilOwnerApproves() {
        BookSummary book = importBook("private", owner);
        String token = libraryService.share(book.id(), owner).shareToken();

        assertThat(libraryService.previewByShareToken(token, stranger).id())
                .isEqualTo(book.id());
        assertThatThrownBy(() -> accessRequestService.request(
                book.id(), new AccessRequestCreate("wrong-token", "想看"), stranger))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).status().value())
                .isEqualTo(403);
        assertThatThrownBy(() -> libraryService.getBook(book.id(), stranger))
                .isInstanceOf(NotFoundException.class);

        AccessRequestView request = accessRequestService.request(
                book.id(), new AccessRequestCreate(token, "想看"), stranger);
        assertThat(request.status()).isEqualTo("PENDING");
        assertThat(accessRequestService.request(
                book.id(), new AccessRequestCreate(token, "再申请"), stranger).id())
                .isEqualTo(request.id());
        assertThatThrownBy(() -> libraryService.getBook(book.id(), stranger))
                .isInstanceOf(NotFoundException.class);

        accessRequestService.approve(request.id(), owner);
        assertThat(libraryService.getBook(book.id(), stranger).book().id())
                .isEqualTo(book.id());
        assertThat(libraryService.listShared(stranger))
                .extracting(BookSummary::id).containsExactly(book.id());

        accessRequestService.revoke(request.id(), owner);
        assertThatThrownBy(() -> libraryService.getBook(book.id(), stranger))
                .isInstanceOf(NotFoundException.class);
        assertThat(libraryService.listShared(stranger)).isEmpty();
    }

    @Test
    void onlyOwnerOrManagerCanModifyPublicBooks() {
        BookSummary book = importBook("public", owner);
        BookUpdateRequest rename = new BookUpdateRequest("新书名", null, null, null, null);

        assertThat(libraryService.update(book.id(), rename, owner).title())
                .isEqualTo("新书名");
        assertThatThrownBy(() -> libraryService.update(book.id(), rename, stranger))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).status().value())
                .isEqualTo(403);
        assertThat(libraryService.update(book.id(), rename, admin).title())
                .isEqualTo("新书名");

        assertThatThrownBy(() -> libraryService.delete(book.id(), stranger))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).status().value())
                .isEqualTo(403);
        libraryService.delete(book.id(), admin);
        assertThatThrownBy(() -> libraryService.getBook(book.id(), owner))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void privateBookMetadataIsOwnerOnly() {
        BookSummary book = importBook("private", owner);
        BookUpdateRequest rename = new BookUpdateRequest("改名", null, null, null, null);

        assertThat(libraryService.update(book.id(), rename, owner).title()).isEqualTo("改名");
        assertThatThrownBy(() -> libraryService.update(book.id(), rename, admin))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> libraryService.delete(book.id(), stranger))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void hiddenPublicBookIsOnlyVisibleToManagers() {
        BookSummary book = importBook("public", owner);
        libraryService.update(book.id(),
                new BookUpdateRequest(null, null, null, null, "HIDDEN"), admin);

        assertThatThrownBy(() -> libraryService.getBook(book.id(), stranger))
                .isInstanceOf(NotFoundException.class);
        assertThat(libraryService.getBook(book.id(), admin).book().status())
                .isEqualTo("HIDDEN");
        // 状态变更（恢复可见）同样需要内容管理权限，书籍所有者也无权自行放行。
        assertThatThrownBy(() -> libraryService.update(book.id(),
                new BookUpdateRequest(null, null, null, null, "VISIBLE"), owner))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).status().value())
                .isEqualTo(403);
    }

    @Test
    void categoriesRequireManagerPermission() {
        Category category = categoryService.create(new CategoryRequest("诗词", 5));
        assertThat(categoryService.list()).extracting(Category::name).contains("诗词");

        BookSummary book = libraryService.importBook("public", "分类书", "作者",
                category.id(), sampleFile(), admin);
        assertThat(book.categoryId()).isEqualTo(category.id());
        assertThat(book.categoryName()).isEqualTo("诗词");

        assertThatThrownBy(() -> libraryService.importBook("public", "越权分类", "作者",
                category.id(), sampleFile(), owner))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).status().value())
                .isEqualTo(403);
    }

    @Test
    void shelvesAreOwnerScopedAndOnlyContainReadableBooks() {
        CurrentUser other = user(14, "别人", "reader.private.manage");
        BookSummary mine = importBook("private", owner);
        BookSummary others = importBook("private", other);

        Shelf shelf = shelfService.create(new ShelfRequest("我的书单", "备注"), owner);
        shelfService.addBook(shelf.id(), mine.id(), owner);
        shelfService.addBook(shelf.id(), mine.id(), owner);
        assertThatThrownBy(() -> shelfService.addBook(shelf.id(), others.id(), owner))
                .isInstanceOf(NotFoundException.class);

        ShelfDetail detail = shelfService.get(shelf.id(), owner);
        assertThat(detail.books()).extracting(BookSummary::id).containsExactly(mine.id());
        assertThat(detail.shelf().bookCount()).isEqualTo(1);

        assertThat(shelfService.list(stranger)).isEmpty();
        assertThatThrownBy(() -> shelfService.get(shelf.id(), stranger))
                .isInstanceOf(NotFoundException.class);

        shelfService.removeBook(shelf.id(), mine.id(), owner);
        assertThat(shelfService.get(shelf.id(), owner).books()).isEmpty();
    }

    private BookSummary importBook(String scope, CurrentUser who) {
        return libraryService.importBook(scope, "测试书", "作者", null, sampleFile(), who);
    }

    private MockMultipartFile sampleFile() {
        return new MockMultipartFile("file", "book.txt", "text/plain",
                SAMPLE_TEXT.getBytes(StandardCharsets.UTF_8));
    }

    private static CurrentUser user(long id, String nickname, String... permissions) {
        return new CurrentUser(id, "u" + id, nickname, "USER",
                List.of("USER"), Set.of(permissions));
    }
}
