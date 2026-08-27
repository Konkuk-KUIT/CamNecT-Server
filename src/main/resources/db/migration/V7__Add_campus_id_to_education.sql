alter table education
    add column campus_id bigint after institution_id;

alter table education
    add constraint fk_education_campus
        foreign key (campus_id) references campuses (campus_id);
