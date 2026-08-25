DELETE utm
  FROM user_tag_map utm
  JOIN users u ON u.user_id = utm.user_id
 WHERE u.status = 'WITHDRAWN';

DELETE uf
  FROM user_follow uf
  LEFT JOIN users follower ON follower.user_id = uf.follower_id
  LEFT JOIN users following_user ON following_user.user_id = uf.following_id
 WHERE follower.status = 'WITHDRAWN'
    OR following_user.status = 'WITHDRAWN';

CREATE INDEX idx_user_follow_following_id
    ON user_follow (following_id);
