CREATE TABLE public.posts
(
    id UUID DEFAULT uuid_generate_v4(),
    location character varying,
    title character varying,
    description character varying,
    PRIMARY KEY (id)
);

ALTER TABLE IF EXISTS public.posts
    OWNER to admin;