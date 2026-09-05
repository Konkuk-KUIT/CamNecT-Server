-- 1. Institutions (동일 학교)
INSERT INTO institutions (institution_code, institution_name_kor, institution_name_eng, sort_order, is_active, created_at, updated_at)
VALUES ('UNIV001', '한국대학교', 'Hankuk University', 1, true, NOW(), NOW()),
       ('UNIV002', '건국대학교', 'Konkuk University', 1, true, NOW(), NOW());

-- -- 2. Colleges (한국대학교 소속의 두 단과대)
-- INSERT INTO colleges (institution_id, college_code, college_name_kor, college_name_eng, sort_order, is_active, created_at, updated_at)
-- VALUES
--     (1, 'COLL_IT', 'IT공과대학', 'College of IT Engineering', 1, true, NOW(), NOW()),
--     (1, 'COLL_BIZ', '경영대학', 'College of Business Administration', 2, true, NOW(), NOW());

-- 3. Majors (각 단과대별 학과)
INSERT INTO majors (institution_id, major_code, major_name_kor, major_name_eng, sort_order, is_active, created_at, updated_at)
VALUES
    (1, 'CS101', '컴퓨터공학과', 'Computer Science and Engineering', 1, true, NOW(), NOW()),
    (1, 'CS102', '컴퓨터과학과', 'Computer Science', 1, true, NOW(), NOW()),
    (1, 'BA201', '경영학과', 'Business Administration', 1, true, NOW(), NOW());

-- 4. Users (기존 유저 2명)
INSERT INTO users (username, password_hash, name, email, status, created_at, updated_at)
VALUES
    ('user01@example.com', '1234', '김철수', 'user01@example.com', 'ACTIVE', NOW(), NOW()),
    ('tester03', '{bcrypt}$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd001RIdL.mG2.J6', '박민수', 'tester03@gmail.com', 'ACTIVE', NOW(), NOW());

-- 5. UserProfile (두 유저 모두 Institution_id 1번 사용)
INSERT INTO user_profile (user_id, bio, open_to_coffeechat, profile_image_url, student_no, year_level, institution_id, major_id)
VALUES
    (1, '백엔드 개발자를 꿈꾸는 한국대 김철수입니다.', true, 'https://example.com/p1.png', '20211234', 3, 1, 1),
    (2, '경영학을 전공하는 한국대 박민수입니다.', false, 'https://example.com/p2.png', '20195678', 4, 1, 2);

-- 1. TagAttribute (정의된 ENUM 값에 맞춤)
INSERT INTO tag_attribute (name)
VALUES
    ('DEPARTMENT'), -- ID: 1 (전공/학과 관련)
    ('TOPIC'),      -- ID: 2 (기술 스택, 주제 관련)
    ('CUSTOM');     -- ID: 3 (사용자 정의 태그 등)

-- 2. Tags (위에서 생성한 TagAttribute ID 참조)
INSERT INTO tags (name, type, category, active, tag_attribute_id, created_at)
VALUES
-- DEPARTMENT (Attribute ID: 1)
('컴퓨터공학', 'MAJOR', '학업', true, 1, NOW()),
('경영학', 'MAJOR', '학업', true, 1, NOW()),

-- TOPIC (Attribute ID: 2)
('Java', 'SKILL', '학업', true, 1, NULL, 2, NOW()),
('Spring Boot', 'SKILL', '학업', true, 1, NULL, 2, NOW()),
('마케팅', 'SKILL', '진로', true, 2, NULL, 2, NOW()),

-- CUSTOM (Attribute ID: 3)
('커피챗환영', 'USER_TAG', '기타', true, NULL, NULL, 3, NOW()),
('활동적인', 'PERSONALITY', '기타', true, NULL, NULL, 3, NOW());

-- user_tag_map 테이블 더미 데이터 삽입
-- 1번 유저 (김철수: 컴퓨터공학과, Java, Spring Boot, 커피챗환영)
INSERT INTO user_tag_map (user_id, tag_id) VALUES (1, 1); -- 컴퓨터공학
INSERT INTO user_tag_map (user_id, tag_id) VALUES (1, 3); -- Java
INSERT INTO user_tag_map (user_id, tag_id) VALUES (1, 4); -- Spring Boot
INSERT INTO user_tag_map (user_id, tag_id) VALUES (1, 6); -- 커피챗환영

-- 2번 유저 (박민수: 경영학, 마케팅, 활동적인)
INSERT INTO user_tag_map (user_id, tag_id) VALUES (2, 2); -- 경영학
INSERT INTO user_tag_map (user_id, tag_id) VALUES (2, 5); -- 마케팅
INSERT INTO user_tag_map (user_id, tag_id) VALUES (2, 7); -- 활동적인

INSERT INTO portfolio_project (user_id, title, thumbnail_url, description, start_date, end_date, is_public, is_favorite, review, created_at, updated_at) VALUES (1, 'CamNecT 백엔드 API 서버 구축', 'https://example.com/thumb/camnect.png', 'Spring Boot와 JPA를 활용한 캠퍼스 네트워킹 플랫폼 백엔드입니다.', '2025-09-01', '2025-12-20', true, true, '협업의 중요성을 깨달은 프로젝트였습니다.', '2026-01-22', '2026-01-22');
INSERT INTO portfolio_project (user_id, title, thumbnail_url, description, start_date, end_date, is_public, is_favorite, review, created_at, updated_at) VALUES (1, '개인 기술 블로그 개발', 'https://example.com/thumb/blog.png', '학습 내용을 정리하기 위해 제작한 블로그입니다.', '2025-05-10', '2025-08-15', true, false, '직접 인프라를 구축하며 많이 배웠습니다.', '2026-01-22', '2026-01-22');
INSERT INTO portfolio_project (user_id, title, thumbnail_url, description, start_date, end_date, is_public, is_favorite, review, created_at, updated_at) VALUES (2, '2025 대학생 마케팅 공모전 출품작', 'https://example.com/thumb/contest.png', 'MZ세대 타겟팅 마케팅 전략 기획서입니다.', '2025-11-01', '2025-11-30', true, true, '데이터 기반 의사결정의 필요성을 느꼈습니다.', '2026-01-22', '2026-01-22');

-- Certificate 테이블 더미 데이터 삽입
-- 1번 유저 (김철수: 백엔드 지망생 자격증)
INSERT INTO certificate (
    user_id,
    certificate_name,
    issuer_name,
    acquired_date,
    expire_date,
    credential_url,
    description
) VALUES
      (
          1,
          '정보처리기사',
          '한국산업인력공단',
          '2024-06-15',
          NULL,
          'https://q-net.or.kr/verify/12345',
          '백엔드 개발의 기초가 되는 국가기술자격증 취득'
      ),
      (
          1,
          'SQL 개발자(SQLD)',
          '한국데이터산업진흥원',
          '2024-09-20',
          '2026-09-20',
          'https://dataq.or.kr/cert/67890',
          '데이터베이스 모델링 및 SQL 활용 능력 검증'
      );

-- 2번 유저 (박민수: 경영/마케팅 지망생 자격증)
INSERT INTO certificate (
    user_id,
    certificate_name,
    issuer_name,
    acquired_date,
    expire_date,
    credential_url,
    description
) VALUES
      (
          2,
          'Google Analytics Individual Qualification',
          'Google',
          '2025-01-10',
          '2026-01-10',
          'https://skillshop.exceedlms.com/student/p/111',
          '데이터 기반 마케팅 분석 역량 확보'
      ),
      (
          2,
          'TOEIC',
          'YBM',
          '2024-12-01',
          '2026-12-01',
          NULL,
          '비즈니스 영어 커뮤니케이션 능력 (900점)'
      );

-- Experience 테이블 더미 데이터 삽입
-- 1번 유저 (김철수: 백엔드 개발 관련 경력)
INSERT INTO experience (
    user_id,
    company_name,
    major_name,
    start_date,
    end_date,
    is_current,
    description
) VALUES
      (
          1,
          '(주)캠넥트 소프트',
          '백엔드 개발 인턴',
          '2025-07-01',
          '2025-12-31',
          false,
          'Java/Spring Boot를 이용한 사내 어드민 페이지 API 개발 및 유지보수'
      ),
      (
          1,
          '구글 코리아',
          '소프트웨어 엔지니어링 캠프',
          '2026-01-05',
          NULL,
          true,
          '현재 재직 중인 교육형 인턴십 과정. 대규모 트래픽 처리 아키텍처 학습 중'
      );

-- 2번 유저 (박민수: 마케팅 및 대외활동 관련 경력)
INSERT INTO experience (
    user_id,
    company_name,
    major_name,
    start_date,
    end_date,
    is_current,
    description
) VALUES
      (
          2,
          '대한대학교 홍보대사',
          '콘텐츠 에디터',
          '2024-03-01',
          '2025-02-28',
          false,
          '학교 공식 인스타그램 콘텐츠 제작 및 이벤트 기획 운영'
      ),
      (
          2,
          '스타트업 넥스트',
          '퍼포먼스 마케팅 인턴',
          '2025-09-01',
          NULL,
          true,
          '현재 재직 중. GA4를 활용한 유입 경로 분석 및 검색 광고(SA) 최적화 업무 보조'
      );




