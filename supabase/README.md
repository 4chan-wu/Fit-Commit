# Fitcommit Supabase

AWS/FastAPI를 제거한 뒤 사용하는 Supabase 서버 코드임.

- 앱 CRUD: Auth JWT + PostgREST + RLS
- 이미지: Storage의 `{auth.uid()}/{category}/{uuid}.jpg`
- AI 채팅: `ai-chat` Edge Function
- 결제 준비/승인: `toss-payments` Edge Function

## Edge Function 배포

Supabase CLI에 로그인한 뒤 프로젝트 루트에서 실행함.

```bash
supabase login
supabase link --project-ref iuplvspexcgvhonqgfag
supabase secrets set --env-file supabase/functions/.env
supabase functions deploy ai-chat
supabase functions deploy toss-payments
```

`supabase/functions/.env`는 Git에 올리지 않으며 다음 키만 넣음.

```env
OPENAI_API_KEY=...
TOSS_CLIENT_KEY=...
TOSS_SECRET_KEY=...
```

Supabase가 제공하는 `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`는 Edge Function 런타임에서 자동으로 사용할 수 있으므로 위 파일에 복사하지 않음.

현재 모바일 앱의 로그인, 옷장, 의류, 이미지 기능에는 Edge Function이 필요하지 않음. AI 채팅과 결제를 실제 앱에 연결할 때만 위 두 함수를 배포함.
