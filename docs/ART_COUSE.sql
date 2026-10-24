--
-- PostgreSQL database dump
--

\restrict kyC5P8jX4Kga2rhAZY3b0H3nCJsUibV988N01yRgqWwchDcptDRbqtchAWv3TLE

-- Dumped from database version 18.4
-- Dumped by pg_dump version 18.4

-- Started on 2026-10-12 16:57:02

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 240 (class 1259 OID 28706)
-- Name: Payment; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public."Payment" (
    id uuid NOT NULL,
    order_id uuid NOT NULL,
    gateway character varying(20) NOT NULL,
    gateway_transaction_id character varying(100) NOT NULL,
    amount numeric NOT NULL,
    status character varying(20) NOT NULL,
    paid_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    commission_amount numeric NOT NULL
);


ALTER TABLE public."Payment" OWNER TO postgres;

--
-- TOC entry 232 (class 1259 OID 28618)
-- Name: child_enroll; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.child_enroll (
    child_id uuid NOT NULL,
    course_id uuid NOT NULL,
    status character varying(20) NOT NULL
);


ALTER TABLE public.child_enroll OWNER TO postgres;

--
-- TOC entry 223 (class 1259 OID 28509)
-- Name: child_profiles; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.child_profiles (
    user_id uuid NOT NULL,
    parent_id uuid NOT NULL,
    display_name character varying(255) NOT NULL,
    birth_year smallint
);


ALTER TABLE public.child_profiles OWNER TO postgres;

--
-- TOC entry 231 (class 1259 OID 28606)
-- Name: children_course; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.children_course (
    id uuid NOT NULL,
    order_id uuid NOT NULL,
    course_id uuid NOT NULL,
    child_id uuid NOT NULL,
    price numeric NOT NULL
);


ALTER TABLE public.children_course OWNER TO postgres;

--
-- TOC entry 238 (class 1259 OID 28683)
-- Name: conversations; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.conversations (
    id uuid NOT NULL,
    parent_id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    lesson_id uuid,
    context_type character varying(50) NOT NULL,
    context_id uuid NOT NULL
);


ALTER TABLE public.conversations OWNER TO postgres;

--
-- TOC entry 235 (class 1259 OID 28647)
-- Name: course_reviews; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.course_reviews (
    id uuid NOT NULL,
    course_id uuid NOT NULL,
    child_id uuid NOT NULL,
    parent_id uuid NOT NULL,
    rating smallint NOT NULL,
    comment text
);


ALTER TABLE public.course_reviews OWNER TO postgres;

--
-- TOC entry 226 (class 1259 OID 28544)
-- Name: courses; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.courses (
    id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    customize_request_id uuid,
    name character varying(255) NOT NULL,
    description text,
    category character varying(100),
    cover_image_url text,
    price numeric NOT NULL,
    status character varying(20) NOT NULL
);


ALTER TABLE public.courses OWNER TO postgres;

--
-- TOC entry 225 (class 1259 OID 28530)
-- Name: customize_requests; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.customize_requests (
    id uuid NOT NULL,
    parent_id uuid NOT NULL,
    title character varying(255) NOT NULL,
    description text NOT NULL,
    category character varying(100),
    child_age smallint NOT NULL,
    budget numeric NOT NULL,
    deadline timestamp with time zone,
    status character varying(20) NOT NULL
);


ALTER TABLE public.customize_requests OWNER TO postgres;

--
-- TOC entry 234 (class 1259 OID 28638)
-- Name: learning_progress; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.learning_progress (
    child_id uuid NOT NULL,
    lesson_id uuid NOT NULL,
    watched_seconds integer NOT NULL,
    is_completed boolean NOT NULL
);


ALTER TABLE public.learning_progress OWNER TO postgres;

--
-- TOC entry 228 (class 1259 OID 28569)
-- Name: lesson_supplies; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.lesson_supplies (
    lesson_id uuid NOT NULL,
    supply_id uuid NOT NULL,
    quantity numeric NOT NULL
);


ALTER TABLE public.lesson_supplies OWNER TO postgres;

--
-- TOC entry 227 (class 1259 OID 28556)
-- Name: lessons; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.lessons (
    id uuid NOT NULL,
    course_id uuid NOT NULL,
    supply_id uuid,
    title character varying(255) NOT NULL,
    video_url text,
    is_trial boolean NOT NULL,
    has_assignment boolean NOT NULL,
    status character varying(20) NOT NULL,
    video_status character varying(20),
    video_error text
);


ALTER TABLE public.lessons OWNER TO postgres;

--
-- TOC entry 239 (class 1259 OID 28693)
-- Name: messages; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.messages (
    id uuid NOT NULL,
    conversation_id uuid NOT NULL,
    sender_id uuid NOT NULL,
    content text NOT NULL,
    is_offer boolean NOT NULL,
    created_at timestamp with time zone NOT NULL
);


ALTER TABLE public.messages OWNER TO postgres;

--
-- TOC entry 242 (class 1259 OID 28734)
-- Name: moderation_reviews; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.moderation_reviews (
    id uuid NOT NULL,
    staff_id uuid NOT NULL,
    target_type character varying(50) NOT NULL,
    target_id uuid NOT NULL,
    decision character varying(100),
    reason text,
    created_at timestamp with time zone NOT NULL
);


ALTER TABLE public.moderation_reviews OWNER TO postgres;

--
-- TOC entry 236 (class 1259 OID 28659)
-- Name: offers; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.offers (
    id uuid NOT NULL,
    request_id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    price numeric NOT NULL,
    message text,
    status character varying(20) NOT NULL
);


ALTER TABLE public.offers OWNER TO postgres;

--
-- TOC entry 229 (class 1259 OID 28579)
-- Name: orders; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.orders (
    id uuid NOT NULL,
    parent_id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    total_amount numeric NOT NULL,
    status character varying(20) NOT NULL,
    expired_at timestamp with time zone,
    shipping_fee numeric NOT NULL
);


ALTER TABLE public.orders OWNER TO postgres;

--
-- TOC entry 230 (class 1259 OID 28592)
-- Name: orders_detail; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.orders_detail (
    id uuid NOT NULL,
    order_id uuid NOT NULL,
    course_id uuid NOT NULL,
    supply_id uuid,
    sub_total numeric NOT NULL,
    shipping_provider character varying(100),
    tracking_code character varying(100),
    status character varying(20) NOT NULL,
    quantity numeric NOT NULL,
    unit_price numeric NOT NULL
);


ALTER TABLE public.orders_detail OWNER TO postgres;

--
-- TOC entry 221 (class 1259 OID 28490)
-- Name: parent_profiles; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.parent_profiles (
    user_id uuid NOT NULL,
    full_name character varying(255) NOT NULL,
    shipping_address text
);


ALTER TABLE public.parent_profiles OWNER TO postgres;

--
-- TOC entry 241 (class 1259 OID 28722)
-- Name: refunds; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.refunds (
    id uuid NOT NULL,
    parent_id uuid NOT NULL,
    total_amount numeric NOT NULL,
    status character varying(20) NOT NULL,
    expired_at timestamp with time zone,
    bank_info text,
    updated_at timestamp with time zone,
    payment_id uuid NOT NULL
);


ALTER TABLE public.refunds OWNER TO postgres;

--
-- TOC entry 233 (class 1259 OID 28626)
-- Name: submissions; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.submissions (
    id uuid NOT NULL,
    child_id uuid NOT NULL,
    lesson_id uuid NOT NULL,
    image_url text NOT NULL,
    status character varying(20) NOT NULL,
    teacher_feedback text,
    badge character varying(100)
);


ALTER TABLE public.submissions OWNER TO postgres;

--
-- TOC entry 224 (class 1259 OID 28517)
-- Name: supplies; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.supplies (
    id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    image_url text,
    price numeric NOT NULL,
    stock_quantity integer NOT NULL,
    status character varying(20) NOT NULL
);


ALTER TABLE public.supplies OWNER TO postgres;

--
-- TOC entry 219 (class 1259 OID 28465)
-- Name: system_config; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.system_config (
    key character varying(100) NOT NULL,
    value text NOT NULL,
    total_amount numeric NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone
);


ALTER TABLE public.system_config OWNER TO postgres;

--
-- TOC entry 222 (class 1259 OID 28499)
-- Name: teacher_profiles; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.teacher_profiles (
    user_id uuid NOT NULL,
    full_name character varying(255) NOT NULL,
    certificates_text text,
    bank_account_info text NOT NULL
);


ALTER TABLE public.teacher_profiles OWNER TO postgres;

--
-- TOC entry 220 (class 1259 OID 28476)
-- Name: users; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    username character varying(255) NOT NULL,
    password text NOT NULL,
    role character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    email character varying(255),
    tokens_valid_after timestamp with time zone
);


ALTER TABLE public.users OWNER TO postgres;

--
-- TOC entry 237 (class 1259 OID 28671)
-- Name: withdrawal_requests; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.withdrawal_requests (
    id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    approved_by uuid NOT NULL,
    total_amount numeric NOT NULL,
    status boolean NOT NULL,
    proof_image_url text
);


ALTER TABLE public.withdrawal_requests OWNER TO postgres;

--
-- TOC entry 5207 (class 0 OID 28706)
-- Dependencies: 240
-- Data for Name: Payment; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public."Payment" (id, order_id, gateway, gateway_transaction_id, amount, status, paid_at, created_at, commission_amount) FROM stdin;
\.


--
-- TOC entry 5199 (class 0 OID 28618)
-- Dependencies: 232
-- Data for Name: child_enroll; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.child_enroll (child_id, course_id, status) FROM stdin;
\.


--
-- TOC entry 5190 (class 0 OID 28509)
-- Dependencies: 223
-- Data for Name: child_profiles; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.child_profiles (user_id, parent_id, display_name, birth_year) FROM stdin;
\.


--
-- TOC entry 5198 (class 0 OID 28606)
-- Dependencies: 231
-- Data for Name: children_course; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.children_course (id, order_id, course_id, child_id, price) FROM stdin;
\.


--
-- TOC entry 5205 (class 0 OID 28683)
-- Dependencies: 238
-- Data for Name: conversations; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.conversations (id, parent_id, teacher_id, lesson_id, context_type, context_id) FROM stdin;
\.


--
-- TOC entry 5202 (class 0 OID 28647)
-- Dependencies: 235
-- Data for Name: course_reviews; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.course_reviews (id, course_id, child_id, parent_id, rating, comment) FROM stdin;
\.


--
-- TOC entry 5193 (class 0 OID 28544)
-- Dependencies: 226
-- Data for Name: courses; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.courses (id, teacher_id, customize_request_id, name, description, category, cover_image_url, price, status) FROM stdin;
\.


--
-- TOC entry 5192 (class 0 OID 28530)
-- Dependencies: 225
-- Data for Name: customize_requests; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.customize_requests (id, parent_id, title, description, category, child_age, budget, deadline, status) FROM stdin;
\.


--
-- TOC entry 5201 (class 0 OID 28638)
-- Dependencies: 234
-- Data for Name: learning_progress; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.learning_progress (child_id, lesson_id, watched_seconds, is_completed) FROM stdin;
\.


--
-- TOC entry 5195 (class 0 OID 28569)
-- Dependencies: 228
-- Data for Name: lesson_supplies; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.lesson_supplies (lesson_id, supply_id, quantity) FROM stdin;
\.


--
-- TOC entry 5194 (class 0 OID 28556)
-- Dependencies: 227
-- Data for Name: lessons; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.lessons (id, course_id, supply_id, title, video_url, is_trial, has_assignment, status) FROM stdin;
\.


--
-- TOC entry 5206 (class 0 OID 28693)
-- Dependencies: 239
-- Data for Name: messages; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.messages (id, conversation_id, sender_id, content, is_offer, created_at) FROM stdin;
\.


--
-- TOC entry 5209 (class 0 OID 28734)
-- Dependencies: 242
-- Data for Name: moderation_reviews; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.moderation_reviews (id, staff_id, target_type, target_id, decision, reason, created_at) FROM stdin;
\.


--
-- TOC entry 5203 (class 0 OID 28659)
-- Dependencies: 236
-- Data for Name: offers; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.offers (id, request_id, teacher_id, price, message, status) FROM stdin;
\.


--
-- TOC entry 5196 (class 0 OID 28579)
-- Dependencies: 229
-- Data for Name: orders; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.orders (id, parent_id, teacher_id, total_amount, status, expired_at, shipping_fee) FROM stdin;
\.


--
-- TOC entry 5197 (class 0 OID 28592)
-- Dependencies: 230
-- Data for Name: orders_detail; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.orders_detail (id, order_id, course_id, supply_id, sub_total, shipping_provider, tracking_code, status, quantity, unit_price) FROM stdin;
\.


--
-- TOC entry 5188 (class 0 OID 28490)
-- Dependencies: 221
-- Data for Name: parent_profiles; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.parent_profiles (user_id, full_name, shipping_address) FROM stdin;
\.


--
-- TOC entry 5208 (class 0 OID 28722)
-- Dependencies: 241
-- Data for Name: refunds; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.refunds (id, parent_id, total_amount, status, expired_at, bank_info, updated_at, payment_id) FROM stdin;
\.


--
-- TOC entry 5200 (class 0 OID 28626)
-- Dependencies: 233
-- Data for Name: submissions; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.submissions (id, child_id, lesson_id, image_url, status, teacher_feedback, badge) FROM stdin;
\.


--
-- TOC entry 5191 (class 0 OID 28517)
-- Dependencies: 224
-- Data for Name: supplies; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.supplies (id, teacher_id, name, description, image_url, price, stock_quantity, status) FROM stdin;
\.


--
-- TOC entry 5186 (class 0 OID 28465)
-- Dependencies: 219
-- Data for Name: system_config; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.system_config (key, value, total_amount, status, created_at) FROM stdin;
\.


--
-- TOC entry 5189 (class 0 OID 28499)
-- Dependencies: 222
-- Data for Name: teacher_profiles; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.teacher_profiles (user_id, full_name, certificates_text, bank_account_info) FROM stdin;
\.


--
-- TOC entry 5187 (class 0 OID 28476)
-- Dependencies: 220
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.users (id, username, password, role, status) FROM stdin;
\.


--
-- TOC entry 5204 (class 0 OID 28671)
-- Dependencies: 237
-- Data for Name: withdrawal_requests; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.withdrawal_requests (id, teacher_id, approved_by, total_amount, status, proof_image_url) FROM stdin;
\.


--
-- TOC entry 4992 (class 2606 OID 28721)
-- Name: Payment Payment_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public."Payment"
    ADD CONSTRAINT "Payment_pkey" PRIMARY KEY (id);


--
-- TOC entry 4976 (class 2606 OID 28625)
-- Name: child_enroll child_enroll_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.child_enroll
    ADD CONSTRAINT child_enroll_pkey PRIMARY KEY (child_id, course_id);


--
-- TOC entry 4958 (class 2606 OID 28516)
-- Name: child_profiles child_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.child_profiles
    ADD CONSTRAINT child_profiles_pkey PRIMARY KEY (user_id);


--
-- TOC entry 4974 (class 2606 OID 28617)
-- Name: children_course children_course_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.children_course
    ADD CONSTRAINT children_course_pkey PRIMARY KEY (id);


--
-- TOC entry 4988 (class 2606 OID 28692)
-- Name: conversations conversations_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT conversations_pkey PRIMARY KEY (id);


--
-- TOC entry 4982 (class 2606 OID 28658)
-- Name: course_reviews course_reviews_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.course_reviews
    ADD CONSTRAINT course_reviews_pkey PRIMARY KEY (id);


--
-- TOC entry 4964 (class 2606 OID 28555)
-- Name: courses courses_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.courses
    ADD CONSTRAINT courses_pkey PRIMARY KEY (id);


--
-- TOC entry 4962 (class 2606 OID 28543)
-- Name: customize_requests customize_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.customize_requests
    ADD CONSTRAINT customize_requests_pkey PRIMARY KEY (id);


--
-- TOC entry 4980 (class 2606 OID 28646)
-- Name: learning_progress learning_progress_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.learning_progress
    ADD CONSTRAINT learning_progress_pkey PRIMARY KEY (child_id, lesson_id);


--
-- TOC entry 4968 (class 2606 OID 28578)
-- Name: lesson_supplies lesson_supplies_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.lesson_supplies
    ADD CONSTRAINT lesson_supplies_pkey PRIMARY KEY (lesson_id, supply_id);


--
-- TOC entry 4966 (class 2606 OID 28568)
-- Name: lessons lessons_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.lessons
    ADD CONSTRAINT lessons_pkey PRIMARY KEY (id);


--
-- TOC entry 4990 (class 2606 OID 28705)
-- Name: messages messages_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_pkey PRIMARY KEY (id);


--
-- TOC entry 4996 (class 2606 OID 28745)
-- Name: moderation_reviews moderation_reviews_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.moderation_reviews
    ADD CONSTRAINT moderation_reviews_pkey PRIMARY KEY (id);


--
-- TOC entry 4984 (class 2606 OID 28670)
-- Name: offers offers_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT offers_pkey PRIMARY KEY (id);


--
-- TOC entry 4972 (class 2606 OID 28605)
-- Name: orders_detail orders_detail_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.orders_detail
    ADD CONSTRAINT orders_detail_pkey PRIMARY KEY (id);


--
-- TOC entry 4970 (class 2606 OID 28591)
-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);


--
-- TOC entry 4954 (class 2606 OID 28498)
-- Name: parent_profiles parent_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parent_profiles
    ADD CONSTRAINT parent_profiles_pkey PRIMARY KEY (user_id);


--
-- TOC entry 4994 (class 2606 OID 28733)
-- Name: refunds refunds_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.refunds
    ADD CONSTRAINT refunds_pkey PRIMARY KEY (id);


--
-- TOC entry 4978 (class 2606 OID 28637)
-- Name: submissions submissions_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.submissions
    ADD CONSTRAINT submissions_pkey PRIMARY KEY (id);


--
-- TOC entry 4960 (class 2606 OID 28529)
-- Name: supplies supplies_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.supplies
    ADD CONSTRAINT supplies_pkey PRIMARY KEY (id);


--
-- TOC entry 4948 (class 2606 OID 28475)
-- Name: system_config system_config_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.system_config
    ADD CONSTRAINT system_config_pkey PRIMARY KEY (key);


--
-- TOC entry 4956 (class 2606 OID 28508)
-- Name: teacher_profiles teacher_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.teacher_profiles
    ADD CONSTRAINT teacher_profiles_pkey PRIMARY KEY (user_id);


--
-- TOC entry 4950 (class 2606 OID 28487)
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- TOC entry 4952 (class 2606 OID 28489)
-- Name: users users_username_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);


ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- TOC entry 4986 (class 2606 OID 28682)
-- Name: withdrawal_requests withdrawal_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.withdrawal_requests
    ADD CONSTRAINT withdrawal_requests_pkey PRIMARY KEY (id);


--
-- TOC entry 5017 (class 2606 OID 28846)
-- Name: child_enroll fk_child_enroll_child; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.child_enroll
    ADD CONSTRAINT fk_child_enroll_child FOREIGN KEY (child_id) REFERENCES public.child_profiles(user_id);


--
-- TOC entry 5018 (class 2606 OID 28851)
-- Name: child_enroll fk_child_enroll_course; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.child_enroll
    ADD CONSTRAINT fk_child_enroll_course FOREIGN KEY (course_id) REFERENCES public.courses(id);


--
-- TOC entry 4999 (class 2606 OID 28761)
-- Name: child_profiles fk_child_profiles_parent; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.child_profiles
    ADD CONSTRAINT fk_child_profiles_parent FOREIGN KEY (parent_id) REFERENCES public.users(id);


--
-- TOC entry 5000 (class 2606 OID 28756)
-- Name: child_profiles fk_child_profiles_user; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.child_profiles
    ADD CONSTRAINT fk_child_profiles_user FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- TOC entry 5014 (class 2606 OID 28841)
-- Name: children_course fk_children_course_child; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.children_course
    ADD CONSTRAINT fk_children_course_child FOREIGN KEY (child_id) REFERENCES public.child_profiles(user_id);


--
-- TOC entry 5015 (class 2606 OID 28836)
-- Name: children_course fk_children_course_course; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.children_course
    ADD CONSTRAINT fk_children_course_course FOREIGN KEY (course_id) REFERENCES public.courses(id);


--
-- TOC entry 5016 (class 2606 OID 28831)
-- Name: children_course fk_children_course_order; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.children_course
    ADD CONSTRAINT fk_children_course_order FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- TOC entry 5030 (class 2606 OID 28921)
-- Name: conversations fk_conversations_lesson; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT fk_conversations_lesson FOREIGN KEY (lesson_id) REFERENCES public.lessons(id);


--
-- TOC entry 5031 (class 2606 OID 28911)
-- Name: conversations fk_conversations_parent; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT fk_conversations_parent FOREIGN KEY (parent_id) REFERENCES public.users(id);


--
-- TOC entry 5032 (class 2606 OID 28916)
-- Name: conversations fk_conversations_teacher; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT fk_conversations_teacher FOREIGN KEY (teacher_id) REFERENCES public.users(id);


--
-- TOC entry 5023 (class 2606 OID 28881)
-- Name: course_reviews fk_course_reviews_child; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.course_reviews
    ADD CONSTRAINT fk_course_reviews_child FOREIGN KEY (child_id) REFERENCES public.child_profiles(user_id);


--
-- TOC entry 5024 (class 2606 OID 28876)
-- Name: course_reviews fk_course_reviews_course; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.course_reviews
    ADD CONSTRAINT fk_course_reviews_course FOREIGN KEY (course_id) REFERENCES public.courses(id);


--
-- TOC entry 5025 (class 2606 OID 28886)
-- Name: course_reviews fk_course_reviews_parent; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.course_reviews
    ADD CONSTRAINT fk_course_reviews_parent FOREIGN KEY (parent_id) REFERENCES public.users(id);


--
-- TOC entry 5003 (class 2606 OID 28781)
-- Name: courses fk_courses_customize_request; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.courses
    ADD CONSTRAINT fk_courses_customize_request FOREIGN KEY (customize_request_id) REFERENCES public.customize_requests(id);


--
-- TOC entry 5004 (class 2606 OID 28776)
-- Name: courses fk_courses_teacher; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.courses
    ADD CONSTRAINT fk_courses_teacher FOREIGN KEY (teacher_id) REFERENCES public.users(id);


--
-- TOC entry 5002 (class 2606 OID 28771)
-- Name: customize_requests fk_customize_requests_parent; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.customize_requests
    ADD CONSTRAINT fk_customize_requests_parent FOREIGN KEY (parent_id) REFERENCES public.users(id);


--
-- TOC entry 5021 (class 2606 OID 28866)
-- Name: learning_progress fk_learning_progress_child; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.learning_progress
    ADD CONSTRAINT fk_learning_progress_child FOREIGN KEY (child_id) REFERENCES public.child_profiles(user_id);


--
-- TOC entry 5022 (class 2606 OID 28871)
-- Name: learning_progress fk_learning_progress_lesson; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.learning_progress
    ADD CONSTRAINT fk_learning_progress_lesson FOREIGN KEY (lesson_id) REFERENCES public.lessons(id);


--
-- TOC entry 5007 (class 2606 OID 28796)
-- Name: lesson_supplies fk_lesson_supplies_lesson; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.lesson_supplies
    ADD CONSTRAINT fk_lesson_supplies_lesson FOREIGN KEY (lesson_id) REFERENCES public.lessons(id);


--
-- TOC entry 5008 (class 2606 OID 28801)
-- Name: lesson_supplies fk_lesson_supplies_supply; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.lesson_supplies
    ADD CONSTRAINT fk_lesson_supplies_supply FOREIGN KEY (supply_id) REFERENCES public.supplies(id);


--
-- TOC entry 5005 (class 2606 OID 28786)
-- Name: lessons fk_lessons_course; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.lessons
    ADD CONSTRAINT fk_lessons_course FOREIGN KEY (course_id) REFERENCES public.courses(id);


--
-- TOC entry 5006 (class 2606 OID 28791)
-- Name: lessons fk_lessons_supply; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.lessons
    ADD CONSTRAINT fk_lessons_supply FOREIGN KEY (supply_id) REFERENCES public.supplies(id);


--
-- TOC entry 5033 (class 2606 OID 28926)
-- Name: messages fk_messages_conversation; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES public.conversations(id);


--
-- TOC entry 5034 (class 2606 OID 28931)
-- Name: messages fk_messages_sender; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES public.users(id);


--
-- TOC entry 5038 (class 2606 OID 28951)
-- Name: moderation_reviews fk_moderation_reviews_staff; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.moderation_reviews
    ADD CONSTRAINT fk_moderation_reviews_staff FOREIGN KEY (staff_id) REFERENCES public.users(id);


--
-- TOC entry 5026 (class 2606 OID 28891)
-- Name: offers fk_offers_request; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT fk_offers_request FOREIGN KEY (request_id) REFERENCES public.customize_requests(id);


--
-- TOC entry 5027 (class 2606 OID 28896)
-- Name: offers fk_offers_teacher; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT fk_offers_teacher FOREIGN KEY (teacher_id) REFERENCES public.users(id);


--
-- TOC entry 5011 (class 2606 OID 28821)
-- Name: orders_detail fk_orders_detail_course; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.orders_detail
    ADD CONSTRAINT fk_orders_detail_course FOREIGN KEY (course_id) REFERENCES public.courses(id);


--
-- TOC entry 5012 (class 2606 OID 28816)
-- Name: orders_detail fk_orders_detail_order; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.orders_detail
    ADD CONSTRAINT fk_orders_detail_order FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- TOC entry 5013 (class 2606 OID 28826)
-- Name: orders_detail fk_orders_detail_supply; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.orders_detail
    ADD CONSTRAINT fk_orders_detail_supply FOREIGN KEY (supply_id) REFERENCES public.supplies(id);


--
-- TOC entry 5009 (class 2606 OID 28806)
-- Name: orders fk_orders_parent; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT fk_orders_parent FOREIGN KEY (parent_id) REFERENCES public.users(id);


--
-- TOC entry 5010 (class 2606 OID 28811)
-- Name: orders fk_orders_teacher; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT fk_orders_teacher FOREIGN KEY (teacher_id) REFERENCES public.users(id);


--
-- TOC entry 4997 (class 2606 OID 28746)
-- Name: parent_profiles fk_parent_profiles_user; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.parent_profiles
    ADD CONSTRAINT fk_parent_profiles_user FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- TOC entry 5035 (class 2606 OID 28936)
-- Name: Payment fk_payment_order; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public."Payment"
    ADD CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- TOC entry 5036 (class 2606 OID 28941)
-- Name: refunds fk_refunds_parent; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.refunds
    ADD CONSTRAINT fk_refunds_parent FOREIGN KEY (parent_id) REFERENCES public.users(id);


--
-- TOC entry 5037 (class 2606 OID 28946)
-- Name: refunds fk_refunds_payment; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.refunds
    ADD CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES public."Payment"(id);


--
-- TOC entry 5019 (class 2606 OID 28856)
-- Name: submissions fk_submissions_child; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.submissions
    ADD CONSTRAINT fk_submissions_child FOREIGN KEY (child_id) REFERENCES public.child_profiles(user_id);


--
-- TOC entry 5020 (class 2606 OID 28861)
-- Name: submissions fk_submissions_lesson; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.submissions
    ADD CONSTRAINT fk_submissions_lesson FOREIGN KEY (lesson_id) REFERENCES public.lessons(id);


--
-- TOC entry 5001 (class 2606 OID 28766)
-- Name: supplies fk_supplies_teacher; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.supplies
    ADD CONSTRAINT fk_supplies_teacher FOREIGN KEY (teacher_id) REFERENCES public.users(id);


--
-- TOC entry 4998 (class 2606 OID 28751)
-- Name: teacher_profiles fk_teacher_profiles_user; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.teacher_profiles
    ADD CONSTRAINT fk_teacher_profiles_user FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- TOC entry 5028 (class 2606 OID 28906)
-- Name: withdrawal_requests fk_withdrawal_requests_approved_by; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.withdrawal_requests
    ADD CONSTRAINT fk_withdrawal_requests_approved_by FOREIGN KEY (approved_by) REFERENCES public.users(id);


--
-- TOC entry 5029 (class 2606 OID 28901)
-- Name: withdrawal_requests fk_withdrawal_requests_teacher; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.withdrawal_requests
    ADD CONSTRAINT fk_withdrawal_requests_teacher FOREIGN KEY (teacher_id) REFERENCES public.users(id);


-- Completed on 2026-10-12 16:57:03

--
-- PostgreSQL database dump complete
--

\unrestrict kyC5P8jX4Kga2rhAZY3b0H3nCJsUibV988N01yRgqWwchDcptDRbqtchAWv3TLE

