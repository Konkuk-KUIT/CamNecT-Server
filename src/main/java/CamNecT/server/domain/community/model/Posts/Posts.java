package CamNecT.server.domain.community.model.Posts;

import CamNecT.server.domain.community.model.Boards;
import CamNecT.server.domain.community.model.enums.PostAccessType;
import CamNecT.server.domain.community.model.enums.PostStatus;
import CamNecT.server.domain.users.model.Users;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Posts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private Boards board;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Lob
    @Column(name = "context", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "is_anonymous", nullable = false)
    private boolean isAnonymous;

    @OneToOne(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private PostStats stats;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PostStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false)
    @Builder.Default
    private PostAccessType accessType = PostAccessType.FREE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    //TODO PostStats 선언을 create안에서..? -> Post post = Posts.builder().....
    public static Posts create(Boards board, Users user, String title, String content, boolean isAnonymous) {
        return Posts.builder()
                .board(board)
                .user(user)
                .title(title)
                .content(content)
                .isAnonymous(isAnonymous)
                .status(PostStatus.PUBLISHED)
                .build();
    }

    public void update(String title, String content) {
        if (title != null) this.title = title;
        if (content != null) this.content = content;
    }

    //public void hide() { this.status = PostStatus.HIDDEN; }

    //public void publish() { this.status = PostStatus.PUBLISHED; }

    public void deleteSoft() {
        this.status = PostStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public void applyAccess(PostAccessType accessType) {
        this.accessType = (accessType == null) ? PostAccessType.FREE : accessType;
    }
}
