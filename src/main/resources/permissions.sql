-- Table: public.permissions

DROP TABLE IF EXISTS public.permissions;

CREATE TABLE public.permissions (
    id UUID DEFAULT uuid_generate_v4(),
    created timestamp with time zone NOT NULL,
    name character varying NOT NULL,
    CONSTRAINT permissions_pkey PRIMARY KEY (id)
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS public.permissions
    OWNER to admin;
