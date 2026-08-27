-- Baseline schema generated from the pre-report-update JPA entity model.
-- Empty databases execute V0 then V1. Existing non-empty databases are marked at version 0
-- by baseline-on-migrate and execute only the report-domain delta in V1.


    create table accepted_comments (
        accepted_comment_id bigint not null auto_increment,
        created_at datetime(6) not null,
        user_id bigint not null,
        comment_id bigint not null,
        post_id bigint not null,
        primary key (accepted_comment_id)
    ) engine=InnoDB;

    create table boards (
        board_id bigint not null auto_increment,
        code enum ('INFO','QUESTION') not null,
        name varchar(30) not null,
        primary key (board_id)
    ) engine=InnoDB;

    create table certificate (
        certificate_id bigint not null auto_increment,
        acquired_date date not null,
        certificate_name varchar(100) not null,
        credential_url varchar(500),
        description TEXT,
        expire_date date,
        issuer_name varchar(100),
        user_id bigint not null,
        primary key (certificate_id)
    ) engine=InnoDB;

    create table chat_request_interests (
        request_id bigint not null,
        tag_id bigint not null
    ) engine=InnoDB;

    create table coffee_chat_message (
        cc_message_id bigint not null auto_increment,
        client_message_id varchar(36),
        content TEXT not null,
        created_at datetime(6) not null,
        is_read bit not null,
        read_at datetime(6),
        updated_at datetime(6) not null,
        receiver_id bigint not null,
        cc_thread_id bigint not null,
        sender_id bigint not null,
        primary key (cc_message_id)
    ) engine=InnoDB;

    create table coffee_chat_request (
        request_id bigint not null auto_increment,
        activity_id bigint,
        request_content TEXT not null,
        created_at datetime(6) not null,
        recruitment_id bigint,
        status enum ('ACCEPTED','CLOSED','REJECTED','WAITING') not null,
        request_type enum ('COFFEE_CHAT','TEAM_RECRUIT') not null,
        receiver_id bigint not null,
        requester_id bigint not null,
        primary key (request_id)
    ) engine=InnoDB;

    create table coffee_chat_thread (
        cc_thread_id bigint not null auto_increment,
        created_at datetime(6) not null,
        last_message_at datetime(6),
        receiver_exited bit not null,
        requester_exited bit not null,
        status ENUM('OPEN', 'CLOSE') not null,
        updated_at datetime(6) not null,
        receiver_id bigint not null,
        request_id bigint not null,
        requester_id bigint not null,
        primary key (cc_thread_id)
    ) engine=InnoDB;

    create table comment_likes (
        comment_like_id bigint not null auto_increment,
        created_at datetime(6) not null,
        user_id bigint not null,
        comment_id bigint not null,
        primary key (comment_like_id)
    ) engine=InnoDB;

    create table comments (
        comment_id bigint not null auto_increment,
        content tinytext not null,
        created_at datetime(6) not null,
        status enum ('DELETED','HIDDEN','PUBLISHED') not null,
        updated_at datetime(6) not null,
        user_id bigint not null,
        parent_comment_id bigint,
        post_id bigint not null,
        primary key (comment_id)
    ) engine=InnoDB;

    create table document_verification_submission (
        id bigint not null auto_increment,
        content_type varchar(100) not null,
        doc_type enum ('ENROLLMENT_CERTIFICATE','GRADUATION_CERTIFICATE') not null,
        original_filename varchar(255) not null,
        reject_reason varchar(500),
        reviewed_at datetime(6),
        reviewer_admin_id bigint,
        size bigint not null,
        status enum ('APPROVED','CANCELED','PENDING','REJECTED') not null,
        storage_key varchar(500) not null,
        submitted_at datetime(6) not null,
        uploaded_at datetime(6) not null,
        user_id bigint not null,
        version bigint,
        primary key (id)
    ) engine=InnoDB;

    create table education (
        education_id bigint not null auto_increment,
        degree varchar(50),
        description TEXT,
        end_date date,
        start_date date not null,
        status enum ('ATTENDING','DROPPED_OUT','EXCHANGE','GRADUATED','LEAVE_OF_ABSENCE','TRANSFERRED') not null,
        institution_id bigint not null,
        major_id bigint,
        user_id bigint not null,
        primary key (education_id)
    ) engine=InnoDB;

    create table email_verification_tokens (
        id bigint not null auto_increment,
        attempt_count integer not null,
        code_hash varchar(64) not null,
        created_at datetime(6) not null,
        email varchar(255) not null,
        expires_at datetime(6) not null,
        used_at datetime(6),
        user_id bigint,
        primary key (id)
    ) engine=InnoDB;

    create table experience (
        experience_id bigint not null auto_increment,
        company_name varchar(100) not null,
        end_date date,
        is_current bit not null,
        start_date date not null,
        user_id bigint not null,
        primary key (experience_id)
    ) engine=InnoDB;

    create table experience_responsibilities (
        experience_id bigint not null,
        responsibility_content TEXT
    ) engine=InnoDB;

    create table external_activities (
        activity_id bigint not null auto_increment,
        apply_end_date date,
        apply_start_date date,
        category enum ('CLUB','EXTERNAL','RECRUITMENT','STUDY') not null,
        context TEXT,
        context_title varchar(200),
        created_at datetime(6) not null,
        official_url varchar(500),
        organizer varchar(100),
        region varchar(50),
        result_announce_date date,
        status enum ('CLOSED','OPEN') not null,
        target_description varchar(300),
        thumbnail_key varchar(500),
        title varchar(200) not null,
        user_id bigint,
        primary key (activity_id)
    ) engine=InnoDB;

    create table external_activities_bookmark (
        activity_bookmark_id bigint not null auto_increment,
        activity_id bigint not null,
        user_id bigint not null,
        primary key (activity_bookmark_id)
    ) engine=InnoDB;

    create table external_activity_attachments (
        attachment_id bigint not null auto_increment,
        created_at datetime(6) not null,
        file_key varchar(500) not null,
        activity_id bigint not null,
        primary key (attachment_id)
    ) engine=InnoDB;

    create table external_activity_tags (
        id bigint not null auto_increment,
        created_at datetime(6) not null,
        activity_id bigint not null,
        tag_id bigint not null,
        primary key (id)
    ) engine=InnoDB;

    create table gifticon_export_batches (
        export_batch_id bigint not null auto_increment,
        created_at datetime(6) not null,
        exported_at datetime(6) not null,
        file_name varchar(200) not null,
        file_path varchar(500) not null,
        item_count integer not null,
        updated_at datetime(6) not null,
        primary key (export_batch_id)
    ) engine=InnoDB;

    create table gifticon_products (
        product_id bigint not null auto_increment,
        brand_name varchar(100) not null,
        created_at datetime(6) not null,
        image_url varchar(500) not null,
        is_active bit not null,
        last_synced_at datetime(6),
        price_points integer not null,
        product_name varchar(200) not null,
        sort_score integer,
        updated_at datetime(6) not null,
        vendor_product_code varchar(100) not null,
        primary key (product_id)
    ) engine=InnoDB;

    create table gifticon_purchases (
        purchase_id bigint not null auto_increment,
        admin_memo varchar(500),
        admin_processed_at datetime(6),
        admin_success bit,
        buyer_email varchar(200),
        buyer_name varchar(100) not null,
        buyer_phone varchar(30),
        client_request_id varchar(100),
        exported_at datetime(6),
        gift_message varchar(500),
        quantity integer not null,
        recipient_name varchar(100),
        recipient_phone varchar(30),
        requested_at datetime(6) not null,
        total_price_points integer not null,
        unit_price_points integer not null,
        export_batch_id bigint,
        product_id bigint not null,
        user_id bigint not null,
        primary key (purchase_id)
    ) engine=InnoDB;

    create table institutions (
        institution_id bigint not null auto_increment,
        created_at datetime(6) not null,
        institution_code varchar(100) not null,
        institution_name_eng varchar(100) not null,
        institution_name_kor varchar(100) not null,
        is_active bit not null,
        sort_order integer not null,
        updated_at datetime(6) not null,
        primary key (institution_id)
    ) engine=InnoDB;

    create table majors (
        major_id bigint not null auto_increment,
        created_at datetime(6) not null,
        is_active bit not null,
        major_code varchar(100) not null,
        major_name_eng varchar(100) not null,
        major_name_kor varchar(100) not null,
        sort_order integer not null,
        updated_at datetime(6) not null,
        institution_id bigint not null,
        primary key (major_id)
    ) engine=InnoDB;

    create table notifications (
        id bigint not null auto_increment,
        actor_user_id bigint,
        comment_id bigint,
        created_at datetime(6) not null,
        link varchar(500),
        message varchar(255) not null,
        post_id bigint,
        is_read bit not null,
        receiver_user_id bigint not null,
        request_id bigint,
        type enum ('ADMIN_ANNOUNCEMENT','CHAT_MESSAGE_RECEIVED','COFFEE_CHAT_ACCEPTED','COFFEE_CHAT_REQUESTED','COMMENT_ACCEPTED','COMMENT_REPLIED','FOLLOWING_POSTED','POINT_EARNED','POINT_SPENT','POST_COMMENTED','TEAM_APPLICATION_RECEIVED','TEAM_RECRUIT_ACCEPTED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table point_transaction (
        point_tx_id bigint not null auto_increment,
        balance_after integer not null,
        created_at datetime(6) not null,
        event_key varchar(64),
        point_change integer not null,
        post_id bigint,
        request_id bigint,
        source_type enum ('ADMIN_ADJUSTMENT','COFFEECHAT_ACCEPTANCE','COMMENT_SELECTION','GIFTICON_PURCHASE','POST_ACCESS_PURCHASE','SIGNUP','THREELIKES_REWARD') not null,
        transaction_type enum ('EARN','SPEND') not null,
        user_id bigint not null,
        primary key (point_tx_id)
    ) engine=InnoDB;

    create table point_wallet (
        wallet_id bigint not null auto_increment,
        balance integer not null,
        created_at datetime(6) not null,
        updated_at datetime(6) not null,
        user_id bigint not null,
        version bigint not null,
        primary key (wallet_id)
    ) engine=InnoDB;

    create table portfolio_asset (
        asset_id bigint not null auto_increment,
        created_at datetime(6) not null,
        file_key varchar(500) not null,
        sort_order integer not null,
        type varchar(20) not null,
        portfolio_id bigint not null,
        primary key (asset_id)
    ) engine=InnoDB;

    create table portfolio_project (
        portfolio_id bigint not null auto_increment,
        assigned_role varchar(255) not null,
        created_at date not null,
        description TEXT,
        end_date date,
        is_favorite bit not null,
        is_public bit not null,
        review TEXT,
        start_date date not null,
        tech_stack varchar(255) not null,
        thumbnail_url varchar(500),
        title varchar(100) not null,
        updated_at date not null,
        user_id bigint not null,
        primary key (portfolio_id)
    ) engine=InnoDB;

    create table post_access (
        post_access_id bigint not null auto_increment,
        paid_points integer not null,
        purchased_at datetime(6) not null,
        post_id bigint not null,
        user_id bigint not null,
        primary key (post_access_id)
    ) engine=InnoDB;

    create table post_attachments (
        attachment_id bigint not null auto_increment,
        created_at datetime(6) not null,
        file_key varchar(500) not null,
        file_size bigint,
        height integer,
        sort_order integer not null,
        status bit not null,
        width integer,
        post_id bigint not null,
        primary key (attachment_id)
    ) engine=InnoDB;

    create table post_bookmarks (
        post_bookmark_id bigint not null auto_increment,
        created_at datetime(6) not null,
        post_id bigint not null,
        user_id bigint not null,
        primary key (post_bookmark_id)
    ) engine=InnoDB;

    create table post_likes (
        post_like_id bigint not null auto_increment,
        created_at datetime(6) not null,
        post_id bigint not null,
        user_id bigint not null,
        primary key (post_like_id)
    ) engine=InnoDB;

    create table post_stats (
        post_stats_id bigint not null auto_increment,
        bookmark_count bigint not null,
        comment_count bigint not null,
        created_at datetime(6) not null,
        hot_score bigint not null,
        last_activity_at datetime(6) not null,
        like_count bigint not null,
        like_rewarded_3 bit not null,
        root_comment_count bigint not null,
        view_count bigint not null,
        post_id bigint not null,
        primary key (post_stats_id)
    ) engine=InnoDB;

    create table post_tags (
        post_tag_id bigint not null auto_increment,
        created_at datetime(6) not null,
        post_id bigint not null,
        tag_id bigint not null,
        primary key (post_tag_id)
    ) engine=InnoDB;

    create table posts (
        post_id bigint not null auto_increment,
        access_type enum ('FREE','POINT_REQUIRED') not null,
        context MEDIUMTEXT not null,
        created_at datetime(6) not null,
        deleted_at datetime(6),
        is_anonymous bit not null,
        status enum ('DELETED','HIDDEN','PUBLISHED') not null,
        title varchar(200) not null,
        updated_at datetime(6) not null,
        board_id bigint not null,
        user_id bigint not null,
        primary key (post_id)
    ) engine=InnoDB;

    create table push_devices (
        push_device_id bigint not null auto_increment,
        device_id varchar(128) not null,
        enabled bit not null,
        fcm_token varchar(512) not null,
        last_seen_at datetime(6) not null,
        platform enum ('ANDROID','WEB') not null,
        user_id bigint not null,
        primary key (push_device_id)
    ) engine=InnoDB;

    create table recruitments_bookmark (
        recruit_bookmark_id bigint not null auto_increment,
        recruit_id bigint not null,
        user_id bigint not null,
        primary key (recruit_bookmark_id)
    ) engine=InnoDB;

    create table report (
        report_id bigint not null auto_increment,
        context TEXT not null,
        created_at datetime(6) not null,
        post_type enum ('ACTIVITY','COMMUNITY','USER'),
        report_category varchar(255) not null,
        reported_post_id bigint,
        reported_user_id bigint not null,
        reporter_id bigint not null,
        status enum ('RECEIVED','REJECTED','RESOLVED') not null,
        title varchar(255) not null,
        updated_at datetime(6) not null,
        primary key (report_id)
    ) engine=InnoDB;

    create table tag_category (
        tag_category_id bigint not null auto_increment,
        active bit not null,
        code varchar(30) not null,
        name varchar(50) not null,
        sort_order integer not null,
        primary key (tag_category_id)
    ) engine=InnoDB;

    create table tag_relation (
        context enum ('POST_ACTIVITY','POST_COMMUNITY','PROFILE') not null,
        from_tag_id bigint not null,
        to_tag_id bigint not null,
        evidence_count integer not null,
        score float(53) not null,
        updated_at datetime(6) not null,
        primary key (context, from_tag_id, to_tag_id)
    ) engine=InnoDB;

    create table tag_stats (
        context enum ('POST_ACTIVITY','POST_COMMUNITY','PROFILE') not null,
        tag_id bigint not null,
        doc_count integer not null,
        updated_at datetime(6) not null,
        primary key (context, tag_id)
    ) engine=InnoDB;

    create table tags (
        tag_id bigint not null auto_increment,
        active bit not null,
        created_at datetime(6) not null,
        name varchar(30) not null,
        tag_category_id bigint not null,
        primary key (tag_id)
    ) engine=InnoDB;

    create table team_applications (
        application_id bigint not null auto_increment,
        content varchar(100) not null,
        created_at datetime(6) not null,
        recruit_id bigint not null,
        status enum ('APPROVED','REJECTED','REQUESTED') not null,
        user_id bigint not null,
        primary key (application_id)
    ) engine=InnoDB;

    create table team_recruitments (
        recruit_id bigint not null auto_increment,
        activity_id bigint,
        bookmark_count integer,
        content TEXT not null,
        created_at datetime(6),
        recruit_count integer,
        recruit_deadline date not null,
        recruit_status enum ('CLOSED','RECRUITING'),
        title varchar(200) not null,
        updated_at datetime(6),
        user_id bigint,
        primary key (recruit_id)
    ) engine=InnoDB;

    create table upload_tickets (
        id bigint not null auto_increment,
        content_type varchar(100) not null,
        created_at datetime(6) not null,
        expires_at datetime(6) not null,
        original_filename varchar(255),
        purpose enum ('ACTIVITY_ATTACHMENT','ACTIVITY_THUMBNAIL','COMMUNITY_POST_ATTACHMENT','PORTFOLIO_ATTACHMENT','PORTFOLIO_THUMBNAIL','PROFILE_IMAGE','VERIFICATION_DOCUMENT') not null,
        size bigint not null,
        status enum ('EXPIRED','PENDING','USED') not null,
        storage_key varchar(500) not null,
        used_at datetime(6),
        used_ref_id bigint,
        used_ref_type enum ('ACTIVITY','PORTFOLIO','POST','USER_PROFILE','VERIFICATION'),
        user_id bigint not null,
        primary key (id)
    ) engine=InnoDB;

    create table user_follow (
        follow_id bigint not null auto_increment,
        created_at datetime(6) not null,
        follower_id bigint not null,
        following_id bigint not null,
        updated_at datetime(6) not null,
        primary key (follow_id)
    ) engine=InnoDB;

    create table user_profile (
        user_id bigint not null,
        bio TEXT,
        initial_setup_completed bit not null,
        institution_id bigint,
        is_certificate_visible bit not null,
        is_education_visible bit not null,
        is_experience_visible bit not null,
        is_follower_visible bit not null,
        major_id bigint,
        open_to_coffeechat bit not null,
        profile_image_key varchar(500),
        student_no varchar(20),
        year_level integer,
        primary key (user_id)
    ) engine=InnoDB;

    create table user_refresh_tokens (
        user_id bigint not null,
        expires_at datetime(6) not null,
        refresh_token_hash varchar(128) not null,
        updated_at datetime(6) not null,
        primary key (user_id)
    ) engine=InnoDB;

    create table user_tag_map (
        user_tag_id bigint not null auto_increment,
        tag_id bigint not null,
        user_id bigint not null,
        primary key (user_tag_id)
    ) engine=InnoDB;

    create table users (
        user_id bigint not null auto_increment,
        created_at datetime(6) not null,
        email varchar(255),
        name varchar(100) not null,
        password_hash varchar(255) not null,
        phone_num varchar(20),
        role enum ('ADMIN','USER') not null,
        status enum ('ACTIVE','ADMIN_PENDING','SUSPENDED','WITHDRAWN') not null,
        updated_at datetime(6) not null,
        username varchar(50) not null,
        primary key (user_id)
    ) engine=InnoDB;

    create index idx_accepted_comments_post
       on accepted_comments (post_id);

    create index idx_accepted_comments_comment
       on accepted_comments (comment_id);

    alter table accepted_comments
       add constraint uk_accepted_comments_post unique (post_id);

    alter table boards
       add constraint uk_boards_code unique (code);

    alter table coffee_chat_message
       add constraint uk_chat_message_room_sender_client_id unique (cc_thread_id, sender_id, client_message_id);

    alter table coffee_chat_thread
       add constraint UKijppk847hgujlx28h6wgvcia1 unique (request_id);

    create index idx_comment_likes_comment
       on comment_likes (comment_id);

    create index idx_comment_likes_user
       on comment_likes (user_id);

    alter table comment_likes
       add constraint uk_comment_likes_comment_user unique (comment_id, user_id);

    create index idx_comments_root_cursor
       on comments (post_id, parent_comment_id, comment_id, status);

    create index idx_comments_children
       on comments (post_id, parent_comment_id, status, created_at, comment_id);

    create index idx_doc_verif_user_status
       on document_verification_submission (user_id, status);

    create index idx_doc_verif_status_submittedAt
       on document_verification_submission (status, submitted_at);

    create index idx_evt_user_used
       on email_verification_tokens (user_id, used_at);

    create index idx_gifticon_exported_at
       on gifticon_export_batches (exported_at);

    create index idx_gifticon_product_active
       on gifticon_products (is_active);

    create index idx_gifticon_product_price
       on gifticon_products (price_points);

    alter table gifticon_products
       add constraint uk_gifticon_vendor_code unique (vendor_product_code);

    create index idx_gifticon_purchase_export
       on gifticon_purchases (export_batch_id);

    create index idx_gifticon_purchase_user
       on gifticon_purchases (user_id);

    alter table gifticon_purchases
       add constraint uk_gifticon_purchase_client_req unique (user_id, client_request_id);

    create index idx_notifications_receiver_id
       on notifications (receiver_user_id);

    create index idx_notifications_receiver_read
       on notifications (receiver_user_id, is_read);

    create index idx_point_tx_user
       on point_transaction (user_id);

    create index idx_point_tx_event_key
       on point_transaction (event_key);

    alter table point_transaction
       add constraint uk_point_tx_event_key unique (event_key);

    alter table point_wallet
       add constraint UKlmmecr5fp14x9nna2x6mu5etc unique (user_id);

    create index idx_post_access_user
       on post_access (user_id);

    create index idx_post_access_post
       on post_access (post_id);

    alter table post_access
       add constraint uk_post_access_user_post unique (user_id, post_id);

    create index idx_post_attach_post_status_sort
       on post_attachments (post_id, status, sort_order, attachment_id);

    create index idx_post_bookmarks_user
       on post_bookmarks (user_id, post_id);

    create index idx_post_bookmarks_post
       on post_bookmarks (post_id);

    alter table post_bookmarks
       add constraint uk_post_bookmarks_post_user unique (post_id, user_id);

    create index idx_post_likes_post
       on post_likes (post_id);

    create index idx_post_likes_user
       on post_likes (user_id);

    alter table post_likes
       add constraint uk_post_likes_post_user unique (post_id, user_id);

    create index idx_post_stats_hot
       on post_stats (hot_score, post_id);

    create index idx_post_stats_last
       on post_stats (last_activity_at, post_id);

    create index idx_post_stats_like
       on post_stats (like_count, post_id);

    create index idx_post_stats_bookmark
       on post_stats (bookmark_count, post_id);

    create index idx_post_stats_root
       on post_stats (root_comment_count, post_id);

    alter table post_stats
       add constraint uk_post_stats_post unique (post_id);

    create index idx_post_tags_tag_post
       on post_tags (tag_id, post_id);

    create index idx_post_tags_post
       on post_tags (post_id);

    alter table post_tags
       add constraint uk_post_tags_post_tag unique (post_id, tag_id);

    create index idx_push_devices_user_enabled
       on push_devices (user_id, enabled);

    create index idx_push_devices_token
       on push_devices (fcm_token);

    alter table push_devices
       add constraint uk_push_devices_user_device unique (user_id, device_id);

    create index idx_tag_category_active_sort
       on tag_category (active, sort_order);

    alter table tag_category
       add constraint UK2ygvbs3q4ftqioi7bkalx9yyg unique (code);

    create index idx_tag_relation_from_score
       on tag_relation (context, from_tag_id, score);

    create index idx_tag_relation_to
       on tag_relation (context, to_tag_id);

    create index idx_tag_stats_context_count
       on tag_stats (context, doc_count);

    create index idx_tags_name
       on tags (name);

    create index idx_tags_category_active
       on tags (tag_category_id, active);

    create index idx_upload_ticket_user_purpose_status
       on upload_tickets (user_id, purpose, status);

    create index idx_upload_ticket_expires
       on upload_tickets (expires_at);

    alter table upload_tickets
       add constraint uk_upload_ticket_storage_key unique (storage_key);

    alter table user_follow
       add constraint uk_user_follow unique (follower_id, following_id);

    alter table user_tag_map
       add constraint uk_user_tag unique (user_id, tag_id);

    alter table users
       add constraint UK6dotkott2kjsp8vw4d0m25fb7 unique (email);

    alter table users
       add constraint UK6xisn9isn9ojnu0sg9fyhaeks unique (phone_num);

    alter table users
       add constraint UKr43af9ap4edm43mmtq01oddj6 unique (username);

    alter table accepted_comments
       add constraint FK9th8jbp0acou8dvo9l0enl6pd
       foreign key (comment_id)
       references comments (comment_id);

    alter table accepted_comments
       add constraint FKeyn5wkc3wqgnphft21vve98om
       foreign key (post_id)
       references posts (post_id);

    alter table certificate
       add constraint FKtnnj9ktwn18vtvap4yuptwxhg
       foreign key (user_id)
       references users (user_id);

    alter table chat_request_interests
       add constraint FK2de9iechoocdicpppnqy6q1qd
       foreign key (tag_id)
       references tags (tag_id);

    alter table chat_request_interests
       add constraint FKmd3nikuumekuwnh659umfxggw
       foreign key (request_id)
       references coffee_chat_request (request_id);

    alter table coffee_chat_message
       add constraint FKlrh2tpftndlqeh1qeep0u0ygx
       foreign key (receiver_id)
       references users (user_id);

    alter table coffee_chat_message
       add constraint FK9c8a3xj42xsvwq0yo2dcx2fvo
       foreign key (cc_thread_id)
       references coffee_chat_thread (cc_thread_id);

    alter table coffee_chat_message
       add constraint FKpixo3rv8x6lnssoj0d9ae4ju5
       foreign key (sender_id)
       references users (user_id);

    alter table coffee_chat_request
       add constraint FKi221mkrqv7rfuyugqgveh9b68
       foreign key (receiver_id)
       references users (user_id);

    alter table coffee_chat_request
       add constraint FKjl43c2eehajfkw2dd96far78w
       foreign key (requester_id)
       references users (user_id);

    alter table coffee_chat_thread
       add constraint FKjmkowrqspyhp0ksctb795x7kh
       foreign key (receiver_id)
       references users (user_id);

    alter table coffee_chat_thread
       add constraint FKcq4arukcjk5fhad9oqmsg85iv
       foreign key (request_id)
       references coffee_chat_request (request_id);

    alter table coffee_chat_thread
       add constraint FKsprjasbbir58fahw160hdmk0b
       foreign key (requester_id)
       references users (user_id);

    alter table comment_likes
       add constraint FK3wa5u7bs1p1o9hmavtgdgk1go
       foreign key (comment_id)
       references comments (comment_id);

    alter table comments
       add constraint FK7h839m3lkvhbyv3bcdv7sm4fj
       foreign key (parent_comment_id)
       references comments (comment_id);

    alter table comments
       add constraint FKh4c7lvsc298whoyd4w9ta25cr
       foreign key (post_id)
       references posts (post_id);

    alter table education
       add constraint FKd02rcokfiilke3nk783rw4dr7
       foreign key (institution_id)
       references institutions (institution_id);

    alter table education
       add constraint FKhc6yss4e6hgobpwb0wrh7d9a
       foreign key (major_id)
       references majors (major_id);

    alter table education
       add constraint FKc8qg4a1sd3texfadb2d7eyhgx
       foreign key (user_id)
       references users (user_id);

    alter table email_verification_tokens
       add constraint FKi1c4mmamlb8keqt74k4lrtwhc
       foreign key (user_id)
       references users (user_id);

    alter table experience
       add constraint FKo8gihpwob4qiyigh869833ove
       foreign key (user_id)
       references users (user_id);

    alter table experience_responsibilities
       add constraint FKtjx3qnj3dyfvrmm7ao61cugft
       foreign key (experience_id)
       references experience (experience_id);

    alter table external_activities
       add constraint FKr13dfli0pprskywjr0n11xlo1
       foreign key (user_id)
       references users (user_id);

    alter table external_activities_bookmark
       add constraint FKea9059psewg6ygm2xfg7kqsyh
       foreign key (activity_id)
       references external_activities (activity_id);

    alter table external_activities_bookmark
       add constraint FKsq4l0muamgo6jevbhkfgm7qv1
       foreign key (user_id)
       references users (user_id);

    alter table external_activity_attachments
       add constraint FKmvqwfcd48vkw4m086s1wjh2h
       foreign key (activity_id)
       references external_activities (activity_id);

    alter table external_activity_tags
       add constraint FKpi1x1919dglqkcjxdx218l0mw
       foreign key (activity_id)
       references external_activities (activity_id);

    alter table external_activity_tags
       add constraint FKruthxuemsbf6ljdc7mu0xs7kn
       foreign key (tag_id)
       references tags (tag_id);

    alter table gifticon_purchases
       add constraint FKrjwldlrrcnkrbtsm71gckhj5a
       foreign key (export_batch_id)
       references gifticon_export_batches (export_batch_id);

    alter table gifticon_purchases
       add constraint FKc4pxlbjfa2hr463ty7ojv0ykt
       foreign key (product_id)
       references gifticon_products (product_id);

    alter table gifticon_purchases
       add constraint FKj4ol7uno4y7c3tmddtu0heq8k
       foreign key (user_id)
       references users (user_id);

    alter table majors
       add constraint FKox2xblp0mkcwfyk9tfx28jtlv
       foreign key (institution_id)
       references institutions (institution_id);

    alter table portfolio_asset
       add constraint FKrpn4dqpknaveaqm5waeeqoxp7
       foreign key (portfolio_id)
       references portfolio_project (portfolio_id);

    alter table post_access
       add constraint FKquh16kts83wesijvv8h9osn3c
       foreign key (post_id)
       references posts (post_id);

    alter table post_access
       add constraint FKq9fgqhvjbmdvqecvinord5l35
       foreign key (user_id)
       references users (user_id);

    alter table post_attachments
       add constraint FKdwocy2l1nlf11ebpfrax6sto1
       foreign key (post_id)
       references posts (post_id);

    alter table post_bookmarks
       add constraint FKclpw1l6wrci96rfj0dtt3bfah
       foreign key (post_id)
       references posts (post_id);

    alter table post_bookmarks
       add constraint FK9b5c09u5arho7ei76d78bn7ww
       foreign key (user_id)
       references users (user_id);

    alter table post_likes
       add constraint FKa5wxsgl4doibhbed9gm7ikie2
       foreign key (post_id)
       references posts (post_id);

    alter table post_likes
       add constraint FKkgau5n0nlewg6o9lr4yibqgxj
       foreign key (user_id)
       references users (user_id);

    alter table post_stats
       add constraint FK4cjiqemioe1o57h1pd4kuem3v
       foreign key (post_id)
       references posts (post_id);

    alter table post_tags
       add constraint FKkifam22p4s1nm3bkmp1igcn5w
       foreign key (post_id)
       references posts (post_id);

    alter table post_tags
       add constraint FKm6cfovkyqvu5rlm6ahdx3eavj
       foreign key (tag_id)
       references tags (tag_id);

    alter table posts
       add constraint FK78qo1gxd85rcxqojt2cpcmuj6
       foreign key (board_id)
       references boards (board_id);

    alter table posts
       add constraint FK5lidm6cqbc7u4xhqpxm898qme
       foreign key (user_id)
       references users (user_id);

    alter table tags
       add constraint FK4swaxkm04gqwal7p6hhhouhqh
       foreign key (tag_category_id)
       references tag_category (tag_category_id);

    alter table user_profile
       add constraint FKcen9d68efivuvkxg92rr3kjb
       foreign key (major_id)
       references majors (major_id);

    alter table user_profile
       add constraint FKuganfwvnbll4kn2a3jeyxtyi
       foreign key (user_id)
       references users (user_id);
