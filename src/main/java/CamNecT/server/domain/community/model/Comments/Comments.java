package CamNecT.server.domain.community.model.Comments;

import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.enums.CommentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "comments",
        indexes = {
                @Index(name = "idx_comments_root_cursor", columnList = "post_id,parent_comment_id,comment_id,status"),
                @Index(name = "idx_comments_children", columnList = "post_id,parent_comment_id,status,created_at,comment_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Comments {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @ToString.Exclude
    private Posts post;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    @ToString.Exclude
    private Comments parent;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Comments create(Posts post, Long userId, Comments parent, String content) {
        return Comments.builder()
                .post(post)
                .userId(userId)
                .parent(parent)
                .content(content)
                .status(CommentStatus.PUBLISHED)
                .build();
    }

    public void update(String content) {
        if (content != null) this.content = content;
    }

    public void hide() { this.status = CommentStatus.HIDDEN; }

    public void deleteSoft() { this.status = CommentStatus.DELETED; }
}
