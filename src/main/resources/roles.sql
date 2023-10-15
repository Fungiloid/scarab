-- Table: public.roles

DROP TABLE IF EXISTS public.roles CASCADE;

CREATE TABLE IF NOT EXISTS public.roles
(
    id UUID DEFAULT uuid_generate_v4(),
    created timestamp with time zone NOT NULL,
    name character varying NOT NULL,
    CONSTRAINT roles_pkey PRIMARY KEY (id)
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS public.roles
    OWNER to admin;