CREATE TABLE public.images
(
    id UUID DEFAULT uuid_generate_v4(),
    created timestamp with time zone NOT NULL,
    location character varying,
    title character varying,
    description character varying,
    PRIMARY KEY (id)
);

ALTER TABLE IF EXISTS public.images
    OWNER to admin;