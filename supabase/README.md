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
supabase functions deploy delete-account
```

`supabase/functions/.env`는 Git에 올리지 않으며 다음 키만 넣음.

```env
OPENAI_API_KEY=...
TOSS_CLIENT_KEY=...
TOSS_SECRET_KEY=...
```

Supabase가 제공하는 `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`는 Edge Function 런타임에서 자동으로 사용할 수 있으므로 위 파일에 복사하지 않음.

로그인, 옷장, 의류, 이미지 기능은 Edge Function 없이 동작함. `delete-account`는 Google Play 계정 삭제 요건을 위해 배포해야 하며, 로그인 사용자의 Storage 사진을 삭제한 뒤 Auth 사용자를 삭제함. AI 채팅과 결제 함수는 해당 기능을 실제 앱에 다시 노출할 때 배포함.
