-- Table: public.groups

DROP TABLE IF EXISTS public.groups CASCADE;

CREATE TABLE IF NOT EXISTS public.groups
(
    id UUID DEFAULT uuid_generate_v4(),
    created timestamp with time zone NOT NULL,
    name character varying NOT NULL,
    CONSTRAINT groups_pkey PRIMARY KEY (id)
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS public.groups
    OWNER to admin;