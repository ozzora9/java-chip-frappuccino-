import oracledb
from flask import Flask, render_template, redirect, url_for, request, flash, session, jsonify, abort, get_flashed_messages
import datetime
from datetime import datetime, date
from flask import request  # ⬅️ 이 줄 추가
import hashlib
import datetime
import os # NLS_LANG 설정을 위해 추가
import re

app = Flask(__name__)
app.secret_key = "cookpick_secret"

from flask import g

@app.before_request
def load_user():
    g.member_no = session.get('member_no')
    g.name = session.get('name')
    g.points = session.get('points')

# -----------------------------
# Oracle DB 연결 정보
# -----------------------------
DB_USER = "system"
DB_PASSWORD = "1234"
DB_DSN = "localhost/XE"
# DB 연결 인코딩 설정 (환경 변수로 처리하므로 주석 처리 또는 제거 가능)
# DB_ENCODING = "cp949"

DEFAULT_PROFILE_IMG = "images/profile.png"
PROFILE_UPLOAD_DIR = os.path.join(app.root_path, "static", "uploads", "profiles")
ALLOWED_PROFILE_EXT = {"png", "jpg", "jpeg", "gif", "webp"}

# -----------------------------
# 비밀번호 해시 함수
# -----------------------------
def hash_password(password):
    return hashlib.sha256(password.encode()).hexdigest()

# -----------------------------
# DB 연결 함수 (중복 제거)
# -----------------------------
def get_db_connection():
    try:
        # NLS_LANG 환경 변수가 설정되어 있으면 encoding 인자 불필요
        conn = oracledb.connect(user=DB_USER, password=DB_PASSWORD, dsn=DB_DSN)
        return conn
    except oracledb.DatabaseError as e:
        flash(f"DB 연결 오류: {e}")
        print(f"DB Connection Error: {e}") # 터미널에도 오류 출력
        return None

# -----------------------------
# 프로필 이미지 유틸
# -----------------------------
def _profile_image_path(member_id):
    """프로필 파일이 있으면 경로 반환, 없으면 None."""
    for ext in ALLOWED_PROFILE_EXT:
        candidate = os.path.join(PROFILE_UPLOAD_DIR, f"profile_{member_id}.{ext}")
        if os.path.exists(candidate):
            return candidate
    return None

def get_profile_image_url(member_id):
    path = _profile_image_path(member_id)
    if path:
        # static 이하 상대 경로로 변환
        rel = os.path.relpath(path, os.path.join(app.root_path, "static")).replace("\\", "/")
        return url_for('static', filename=rel)
    return url_for('static', filename=DEFAULT_PROFILE_IMG)
    
# ---------------
# ----------------

def add_notification(member_no, message):
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("""
            INSERT INTO NOTIFICATIONS (MEMBER_NO, MESSAGE)
            VALUES (:1, :2)
        """, [member_no, message])

        cur.execute("""
            DELETE FROM NOTIFICATIONS
            WHERE NOTI_ID IN (
                SELECT NOTI_ID FROM NOTIFICATIONS
                WHERE MEMBER_NO = :1
                ORDER BY CREATED_AT DESC
                OFFSET 15 ROWS
            )
        """, [member_no])

        conn.commit()
    finally:
        cur.close()
        conn.close()


# ---------------
# ----------------
def to_date(val):
    if not val:
        return None
    if isinstance(val, datetime.datetime):
        return val.date()
    if isinstance(val, datetime.date):
        return val
    return datetime.datetime.strptime(val, "%Y-%m-%d").date()

# -----------------------------
# 수량 파싱/표시 유틸
# -----------------------------
def parse_qty(qty_text):
    """
    수량 문자열에서 숫자를 추출해 float로 변환. 숫자가 없으면 기본 1개로 처리.
    예: '2개', '1.5kg', '3 판(15구)' -> 2, 1.5, 3
    """
    if not qty_text:
        return 1.0
    text = str(qty_text).replace(",", "")
    m = re.search(r"([0-9]+(?:\\.[0-9]+)?)", text)
    if m:
        try:
            return float(m.group(1))
        except ValueError:
            return 1.0
    return 1.0

def format_qty(original, num):
    """
    남은 수량 숫자를 원래 문자열의 단위를 최대한 유지해 반환.
    숫자만 있으면 기본 단위 '개'를 붙인다.
    """
    num = max(num, 0)
    num_str = str(int(num)) if abs(num - int(num)) < 1e-9 else ("%.2f" % num).rstrip("0").rstrip(".")
    suffix = ""
    if original:
        m = re.search(r"([0-9]+(?:\\.[0-9]+)?)", str(original))
        if m:
            suffix = str(original)[m.end():]
        else:
            suffix = str(original)
    suffix = suffix or "개"
    return f"{num_str}{suffix}"


# -----------------------------
# 온보딩 페이지
# -----------------------------
@app.route('/')
def onboarding_page():
    return render_template('OnBoarding.html')

# -----------------------------
# 회원가입
# -----------------------------
@app.route('/signup', methods=['GET', 'POST'])
def signup_page():
    if request.method == 'POST':
        user_id = request.form['signupId'].strip()
        pw = request.form['signupPw']
        pw_check = request.form['signupPwCheck']
        name = request.form['signupName'].strip()
        email = request.form['signupEmail'].strip()
        phone = request.form['signupPhone'].strip()
        birth_date = request.form['signupBirth']
        gender = request.form['signupGender']

        if not request.form.get('agreeTerms'):
            flash("이용약관에 동의해야 가입이 가능합니다.")
            return redirect(url_for('signup_page'))

        if pw != pw_check:
            flash("비밀번호가 일치하지 않습니다.")
            return redirect(url_for('signup_page'))

        password_hash = hash_password(pw)

        conn = get_db_connection()
        if not conn: return redirect(url_for('signup_page'))

        cur = conn.cursor()
        try:
            cur.execute("""
                INSERT INTO MEMBERS (USER_ID, PASSWORD_HASH, NAME, EMAIL, PHONE, BIRTH_DATE, GENDER)
                VALUES (:user_id, :password_hash, :name, :email, :phone, TO_DATE(:birth_date,'YYYY-MM-DD'), :gender)
            """, {
                "user_id": user_id, "password_hash": password_hash, "name": name,
                "email": email, "phone": phone, "birth_date": birth_date, "gender": gender
            })
            conn.commit()
            flash(f"{name}님, Cook+Pick 회원가입이 완료되었습니다!")
            return redirect(url_for('login_page'))
        except oracledb.DatabaseError as e:
            conn.rollback()
            if 'ORA-00001' in str(e):
                 flash(f"이미 사용 중인 아이디({user_id})입니다.")
            else:
                 flash(f"회원가입 중 DB 오류 발생: {e}")
                 print(f"Signup Error: {e}") # 터미널 오류 출력
            return redirect(url_for('signup_page'))
        finally:
            if cur: cur.close()
            if conn: conn.close()

    return render_template('signup.html')

# -----------------------------
# 로그인
# -----------------------------
@app.route('/login', methods=['GET', 'POST'])
def login_page():
    if request.method == 'POST':
        user_id = request.form['loginId']
        pw = request.form['loginPw']

        conn = get_db_connection()
        if not conn: return redirect(url_for('login_page'))

        cur = conn.cursor()
        user = None
        try:
            cur.execute("""
                SELECT MEMBER_NO, NAME, PASSWORD_HASH, POINTS
                FROM MEMBERS WHERE USER_ID = :user_id
            """, {"user_id": user_id})
            user = cur.fetchone()
        except oracledb.DatabaseError as e:
            flash(f"로그인 중 DB 오류 발생: {e}")
            print(f"Login DB Error: {e}") # 터미널 오류 출력
        finally:
            if cur: cur.close()
            if conn: conn.close()

        if user and hash_password(pw) == user[2]:
            session['member_no'] = user[0]
            session['name'] = user[1]
            session['user_id'] = user_id
            session['points'] = user[3]
            return redirect(url_for('main_page'))
        else:
            flash("아이디 또는 비밀번호가 올바르지 않습니다.")
            return redirect(url_for('login_page'))

    return render_template('login.html')

# -----------------------------
# 메인 페이지 (홈 화면)
# -----------------------------
@app.route('/main')
def main_page():
    if 'member_no' not in session:
        return redirect(url_for('login_page'))

    member_no = session['member_no']

    conn = get_db_connection()
    cur = conn.cursor()

    try:
        # 사용자 정보
        cur.execute("""
            SELECT NAME, USER_ID, POINTS
            FROM MEMBERS
            WHERE MEMBER_NO = :1
        """, [member_no])
        row = cur.fetchone()
        user_info = {
            "member_no": member_no,
            "name": row[0],
            "user_id": row[1],
            "points": row[2],
            "xpToNext": 2000,  # 임시로 고정값
            "profile_image": get_profile_image_url(member_no)
        }

        # ✅ 유통기한 임박 재료 조회
        cur.execute("""
            SELECT
                M.NAME,
                I.EXPIRATION_DATE
            FROM REFRIGERATOR_ITEMS I
            JOIN INGREDIENTS M ON M.INGREDIENT_ID = I.INGREDIENT_ID
            WHERE I.MEMBER_NO = :1
              AND I.EXPIRATION_DATE IS NOT NULL
        """, [member_no])
        rows_exp = cur.fetchall()

        from datetime import date
        today = date.today()

        impending_expiry_items = []
        for name, exp_date in rows_exp:
            days_left = (exp_date.date() - today).days
            if days_left <= 5:
                impending_expiry_items.append({
                    "name": name,
                    "days_left": days_left
                })

        # ✅ 알림 불러오기
        cur.execute("""
            SELECT NOTI_ID, MESSAGE, CREATED_AT
            FROM NOTIFICATIONS
            WHERE MEMBER_NO = :1
            ORDER BY CREATED_AT DESC
        """, [member_no])
        notifications = cur.fetchall()

    finally:
        cur.close()
        conn.close()

    return render_template(
        'main.html',
        user_info=user_info,
        notifications=notifications,
        impending_expiry_items=impending_expiry_items  # ✅ 여기가 중요
    )




# ----------------------------------------------------
# 레시피 추천 페이지 (검색 기능)
# ----------------------------------------------------
@app.route('/recipes')
def recipes_page():
    search = request.args.get('search', '').strip()
    selected_sorts = request.args.getlist('sort')
    member_no = g.member_no or -1  # 비로그인 대비 (매칭 0 처리)

    conn = get_db_connection(); cur = conn.cursor()

    base_select = """
        SELECT
            r.RECIPE_ID,
            r.TITLE,
            r.DESCRIPTION,
            r.MAIN_IMAGE_URL,
            m.NAME AS AUTHOR_NAME,
            r.AUTHOR_NO,
            NVL(r.AVG_RATING, 0) AS AVG_RATING,
            /* ✅ 총 재료 수 */
            (SELECT COUNT(*)
               FROM RECIPE_INGREDIENTS ri
              WHERE ri.RECIPE_ID = r.RECIPE_ID) AS TOTAL_INGS,
            /* ✅ 내 냉장고와 매칭된 재료 수 */
            (SELECT COUNT(*)
               FROM RECIPE_INGREDIENTS ri
              WHERE ri.RECIPE_ID = r.RECIPE_ID
                AND ri.INGREDIENT_ID IN (
                    SELECT INGREDIENT_ID
                      FROM REFRIGERATOR_ITEMS
                     WHERE MEMBER_NO = :member_no
                )) AS MATCHED_INGS,
            (SELECT COUNT(*)
               FROM REVIEWS rv
              WHERE rv.RECIPE_ID = r.RECIPE_ID) AS REVIEW_COUNT
        FROM RECIPES r
        LEFT JOIN MEMBERS m ON m.MEMBER_NO = r.AUTHOR_NO
    """

    if search:
        sql = base_select + """
            WHERE LOWER(r.TITLE) LIKE LOWER(:search)
            ORDER BY r.RECIPE_ID DESC
        """
        cur.execute(sql, {"member_no": member_no, "search": f"%{search}%"})
    else:
        sql = base_select + " ORDER BY r.RECIPE_ID DESC"
        cur.execute(sql, {"member_no": member_no})

    rows = cur.fetchall()
    cur.close(); conn.close()

    recipes = []
    for r in rows:
        total = int(r[7] or 0)
        matched = int(r[8] or 0)
        feasibility = int(round((matched / total) * 100)) if total > 0 else 0

        recipes.append({
            "id": r[0],
            "title": r[1],
            "description": r[2],
            "image": r[3],
            "author": r[4],
            "author_id": r[5],
            "rating": float(r[6] or 0),
            "feasibility": feasibility,    # ✅ 여기!
            "matched_count": matched,      # (원하면 템플릿에 뱃지로 표시)
            "total_count": total,
            "review_count": int((r[9] if len(r) > 9 else 0) or 0),
        })
    # 기본 정렬: 구현도 ↓, 별점 ↓, 리뷰수 ↓ (냉장고 다중 선택 시 가독성)
    recipes.sort(key=lambda x: (x['feasibility'], x['rating'], x['review_count']), reverse=True)

    # 선택된 정렬 기준이 있으면 그것으로 덮어쓰기
    if selected_sorts:
        key_fields = []
        if 'feasibility' in selected_sorts:
            key_fields.append('feasibility')
        if 'rating' in selected_sorts:
            key_fields.append('rating')
        if 'reviews' in selected_sorts:
            key_fields.append('review_count')
        if key_fields:
            recipes.sort(key=lambda x: tuple(x[k] for k in key_fields), reverse=True)

    return render_template('recipes.html', recipes=recipes, search_term=search, selected_sorts=selected_sorts)



# -----------------------------------------------------------
# [신규] 레시피 상세 페이지
# -----------------------------------------------------------
@app.route('/recipe/<int:recipe_id>')
def recipe_detail(recipe_id):
    if 'member_no' not in session:
        return redirect(url_for('login_page'))

    member_no = session['member_no']
    recipe_data = {}
    
    conn = get_db_connection()
    if not conn:
        flash("DB 연결에 실패했습니다.")
        return redirect(url_for('recipes_page'))

    cur = conn.cursor()
    try:
        # 1) 기본정보 + AUTHOR_NO 같이 가져오기
        cur.execute("""
            SELECT 
                R.RECIPE_ID,
                R.TITLE,
                R.DESCRIPTION,
                M.NAME AS AUTHOR_NAME,
                R.MAIN_IMAGE_URL,
                R.AVG_RATING,
                R.AUTHOR_NO
            FROM RECIPES R
            LEFT JOIN MEMBERS M ON R.AUTHOR_NO = M.MEMBER_NO
            WHERE R.RECIPE_ID = :id
        """, {"id": recipe_id})
        recipe_row = cur.fetchone()
        if not recipe_row:
            flash("존재하지 않는 레시피입니다.")
            return redirect(url_for('recipes_page'))

        recipe_data = {
            "id": recipe_row[0],
            "title": recipe_row[1],
            "description": recipe_row[2],
            "author": recipe_row[3] or 'Cook+Pick',
            "image": recipe_row[4] or url_for('static', filename='images/recipe_default.png'),
            "rating": float(recipe_row[5] or 0),
            "author_no": recipe_row[6],  # ← 템플릿에서 session.member_no와 비교용
            "owned_ingredients": [],
            "needed_ingredients": [],
            "steps": [],
            "reviews": []
        }

        # 2) 내 냉장고 소유 재료 ID
        cur.execute("""
            SELECT INGREDIENT_ID
            FROM REFRIGERATOR_ITEMS
            WHERE MEMBER_NO = :member_no
        """, {"member_no": member_no})
        owned_ingredient_ids = {row[0] for row in cur.fetchall()}

        # 3) 레시피 재료 (보유/부족 분리)
        cur.execute("""
            SELECT RI.INGREDIENT_ID, I.NAME, RI.REQUIRED_QUANTITY
            FROM RECIPE_INGREDIENTS RI
            JOIN INGREDIENTS I ON RI.INGREDIENT_ID = I.INGREDIENT_ID
            WHERE RI.RECIPE_ID = :id
        """, {"id": recipe_id})
        for ing_row in cur.fetchall():
            ingredient = {
                "id": ing_row[0],
                "name": ing_row[1],
                "quantity": ing_row[2] or '적당량'
            }
            (recipe_data["owned_ingredients"] if ingredient["id"] in owned_ingredient_ids
             else recipe_data["needed_ingredients"]).append(ingredient)

        # 4) 조리 순서
        cur.execute("""
            SELECT STEP_NUMBER, INSTRUCTION, IMAGE_URL
            FROM RECIPE_STEPS
            WHERE RECIPE_ID = :id
            ORDER BY STEP_NUMBER
        """, {"id": recipe_id})
        recipe_data["steps"] = [
            {"number": row[0], "instruction": row[1], "image": row[2]}
            for row in cur.fetchall()
        ]

        # 5) 리뷰
        cur.execute("""
            SELECT R.RATING, R.COMMENT_TEXT, M.NAME AS REVIEWER_NAME, R.CREATED_AT, M.MEMBER_NO
            FROM REVIEWS R
            JOIN MEMBERS M ON R.MEMBER_NO = M.MEMBER_NO
            WHERE R.RECIPE_ID = :id
            ORDER BY R.CREATED_AT DESC
        """, {"id": recipe_id})
        recipe_data["reviews"] = [
            {
                "rating": float(row[0]),
                "comment": row[1],
                "author": row[2],
                "author_id": row[4],
                # 템플릿에서 그대로 출력할 수 있게 문자열로 포맷
                "date": (row[3].strftime('%Y-%m-%d') if hasattr(row[3], "strftime") else str(row[3])[:10])
            }
            for row in cur.fetchall()
        ]

    except oracledb.DatabaseError as e:
        flash(f"데이터를 불러오는 중 오류가 발생했습니다: {e}")
        print(f"Recipe Detail Error: {e}")
        return redirect(url_for('recipes_page'))
    finally:
        if cur: cur.close()
        if conn: conn.close()

    # 6) back_url 계산 (my/recipes에서 들어온 경우 뒤로가기 처리)
    from_page = request.args.get('from_page')
    if from_page == 'my':
        back_url = url_for('my_recipes_page')
    elif from_page == 'recipes':
        back_url = url_for('recipes_page')
    else:
        back_url = None  # 없으면 템플릿에서 history.back() 사용

    return render_template('recipe_detail.html', recipe=recipe_data, back_url=back_url)


@app.route('/recipe/<int:recipe_id>/cook_complete', methods=['POST'])
def cook_complete(recipe_id):
    if 'member_no' not in session:
        flash("로그인이 필요합니다.")
        return redirect(url_for('login_page'))

    member_no = session['member_no']

    conn = get_db_connection()
    if not conn:
        flash("DB 연결 실패")
        return redirect(url_for('recipe_detail', recipe_id=recipe_id))

    cur = conn.cursor()
    try:
        # 1) 레시피에 필요한 재료 ID + 필요 수량 수집
        cur.execute("""
            SELECT INGREDIENT_ID, NVL(REQUIRED_QUANTITY, '1')
            FROM RECIPE_INGREDIENTS
            WHERE RECIPE_ID = :1
        """, [recipe_id])
        rows = cur.fetchall()
        ing_rows = [(int(r[0]), r[1]) for r in rows]

        if not ing_rows:
            flash("레시피에 등록된 재료가 없습니다.")
            return redirect(url_for('recipe_detail', recipe_id=recipe_id))

        removed_any = False
        lacking = []

        # 2) 내 냉장고 재료를 필요 수량만큼 차감 (부족하면 가능한 만큼만 차감)
        for ing_id, req_qty_text in ing_rows:
            need = parse_qty(req_qty_text)
            if need <= 0:
                continue

            cur.execute("""
                SELECT ITEM_ID, QUANTITY
                  FROM REFRIGERATOR_ITEMS
                 WHERE MEMBER_NO = :1 AND INGREDIENT_ID = :2
                 ORDER BY EXPIRATION_DATE NULLS LAST, ITEM_ID
            """, [member_no, ing_id])
            fridge_items = cur.fetchall()

            remaining = need
            for item_id, qty_text in fridge_items:
                if remaining <= 1e-9:
                    break
                current_qty = parse_qty(qty_text)
                if current_qty <= 0:
                    cur.execute("DELETE FROM REFRIGERATOR_ITEMS WHERE ITEM_ID = :1 AND MEMBER_NO = :2", [item_id, member_no])
                    continue

                if current_qty <= remaining + 1e-9:
                    # 필요 수량이 같거나 더 많으면 해당 행 삭제
                    cur.execute("DELETE FROM REFRIGERATOR_ITEMS WHERE ITEM_ID = :1 AND MEMBER_NO = :2", [item_id, member_no])
                    removed_any = True
                    remaining -= current_qty
                else:
                    # 일부만 차감 후 남은 수량 업데이트
                    new_qty = current_qty - remaining
                    new_qty_text = format_qty(qty_text, new_qty)
                    cur.execute("""
                        UPDATE REFRIGERATOR_ITEMS
                           SET QUANTITY = :1
                         WHERE ITEM_ID = :2 AND MEMBER_NO = :3
                    """, [new_qty_text, item_id, member_no])
                    removed_any = True
                    remaining = 0
                    break

            if remaining > 1e-9:
                lacking.append(ing_id)

        conn.commit()
        if lacking and removed_any:
            flash("요리 완료! 일부 재료가 부족해 가능한 만큼만 차감했습니다.")
        elif lacking and not removed_any:
            flash("요리 완료! 필요한 재료가 냉장고에 없습니다.")
        elif removed_any:
            flash("요리 완료! 레시피에 필요한 만큼만 냉장고에서 차감되었습니다.")
        else:
            flash("요리 완료! 차감할 재료가 없습니다.")
    except Exception as e:
        conn.rollback()
        flash(f"요리 완료 처리 중 오류가 발생했습니다: {e}")
    finally:
        cur.close(); conn.close()

    return redirect(url_for('recipe_detail', recipe_id=recipe_id))




# -----------------------------
# 내 냉장고 페이지
# -----------------------------
@app.route('/refrigerator', endpoint='refrigerator_page')
def refrigerator_page():
    if not g.member_no:
        return redirect(url_for('login_page'))

    q = (request.args.get('q') or '').strip()

    try:
        conn = get_db_connection()
        if not conn:
            return "DB 연결 오류"

        cur = conn.cursor()

        base_sql = """
            SELECT
                I.ITEM_ID,
                M.INGREDIENT_ID,
                M.NAME,
                I.QUANTITY,
                I.PURCHASE_DATE,
                I.EXPIRATION_DATE,
                I.IS_EXPIRATION_UNKNOWN
            FROM REFRIGERATOR_ITEMS I
            JOIN INGREDIENTS M ON M.INGREDIENT_ID = I.INGREDIENT_ID
            WHERE I.MEMBER_NO = :1
        """
        binds = [g.member_no]

        if q:
            base_sql += " AND LOWER(M.NAME) LIKE LOWER(:2) "
            binds.append(f"%{q}%")

        base_sql += " ORDER BY NVL(I.EXPIRATION_DATE, DATE '2999-12-31'), M.NAME"

        cur.execute(base_sql, binds)
        rows = cur.fetchall()
    finally:
        if cur: cur.close()
        if conn: conn.close()

    groups = {}
    today = date.today()
    for r in rows:
        purchase = r[4]
        expire = r[5]
        purchase_date = purchase.date() if hasattr(purchase, "date") else purchase
        expire_date = expire.date() if hasattr(expire, "date") else expire
        days_left = (expire_date - today).days if expire_date else None
        ing_id = r[1]
        ing_name = r[2]
        item = {
            "item_id": r[0],
            "ingredient_id": ing_id,
            "name": ing_name,
            "quantity": r[3],
            "purchase_date": purchase_date,
            "expiration_date": expire_date,
            "is_exp_unknown": r[6],
            "days_left": days_left,
        }
        grp = groups.setdefault(ing_id, {"name": ing_name, "items_list": []})
        grp["items_list"].append(item)

    grouped_items = []
    for ing_id, grp in groups.items():
        items_sorted = sorted(grp["items_list"], key=lambda x: (x["expiration_date"] or date.max, x["item_id"]))
        total_qty_num = sum(parse_qty(it["quantity"]) for it in items_sorted)
        earliest_exp = items_sorted[0]["expiration_date"] if items_sorted else None
        earliest_days = (earliest_exp - today).days if earliest_exp else None
        grouped_items.append({
            "ingredient_id": ing_id,
            "name": grp["name"],
            "count": len(items_sorted),
            "total_qty": total_qty_num,
            "earliest_expiration": earliest_exp,
            "earliest_days_left": earliest_days,
            "items_list": items_sorted,
        })

    grouped_items.sort(key=lambda g: (g["earliest_expiration"] or date.max, g["name"]))

    return render_template('RefrigeratorMenu.html', grouped_items=grouped_items, now=today, q=q)


# ----------------- 냉장고: 재료 등록/수정/삭제 -----------------
@app.route('/ingredients/new', methods=['GET'])
def ingredient_new():
    if not g.member_no:
        return redirect(url_for('login_page'))
    return render_template('add_ingredient.html', ingredient=None,
                           action=url_for('ingredient_create'))


@app.route('/ingredients', methods=['POST'])
def ingredient_create():
    if not g.member_no:
        return redirect(url_for('login_page'))

    name = (request.form.get('name') or '').strip()
    quantity = (request.form.get('quantity') or '').strip()
    purchase_date = request.form.get('purchase_date')      # YYYY-MM-DD (required)
    expiration_date = request.form.get('expiration_date')  # YYYY-MM-DD or ''
    is_unknown = 'Y' if request.form.get('is_expiration_unknown') == 'on' else 'N'

    if is_unknown == 'Y':
        expiration_date = None

    if not name or not purchase_date:
        return "재료명과 구매일은 필수입니다.", 400

    try:
        with get_db_connection() as conn, conn.cursor() as cur:
            # INGREDIENTS 보장
            cur.execute("SELECT INGREDIENT_ID FROM INGREDIENTS WHERE NAME = :1", [name])
            row = cur.fetchone()
            if row:
                ingredient_id = int(row[0])
            else:
                out_id = cur.var(oracledb.NUMBER)
                cur.execute("""
                    INSERT INTO INGREDIENTS (NAME)
                    VALUES (:1)
                    RETURNING INGREDIENT_ID INTO :2
                """, [name, out_id])
                val = out_id.getvalue()
                if isinstance(val, list):
                    val = val[0]
                ingredient_id = int(val)

            # REFRIGERATOR_ITEMS 추가
            if expiration_date:
                cur.execute("""
                    INSERT INTO REFRIGERATOR_ITEMS
                      (MEMBER_NO, INGREDIENT_ID, QUANTITY, PURCHASE_DATE,
                       EXPIRATION_DATE, IS_EXPIRATION_UNKNOWN)
                    VALUES
                      (:1, :2, :3, TO_DATE(:4, 'YYYY-MM-DD'),
                       TO_DATE(:5, 'YYYY-MM-DD'), :6)
                """, [g.member_no, ingredient_id, quantity, purchase_date, expiration_date, is_unknown])
            else:
                cur.execute("""
                    INSERT INTO REFRIGERATOR_ITEMS
                      (MEMBER_NO, INGREDIENT_ID, QUANTITY, PURCHASE_DATE,
                       EXPIRATION_DATE, IS_EXPIRATION_UNKNOWN)
                    VALUES
                      (:1, :2, :3, TO_DATE(:4, 'YYYY-MM-DD'),
                       NULL, :5)
                """, [g.member_no, ingredient_id, quantity, purchase_date, is_unknown])

            conn.commit()
        return redirect(url_for('refrigerator_page'))
    except Exception as e:
        print("등록 오류:", e)
        return f"등록 오류: {e}"

@app.route('/ingredients/<int:item_id>/edit', methods=['GET'])
def ingredient_edit(item_id):
    if not g.member_no:
        return redirect(url_for('login_page'))

    try:
        with get_db_connection() as conn, conn.cursor() as cur:
            cur.execute("""
                SELECT I.ITEM_ID, M.NAME, I.QUANTITY,
                       I.PURCHASE_DATE, I.EXPIRATION_DATE, I.IS_EXPIRATION_UNKNOWN
                FROM REFRIGERATOR_ITEMS I
                JOIN INGREDIENTS M ON M.INGREDIENT_ID = I.INGREDIENT_ID
                WHERE I.ITEM_ID = :1 AND I.MEMBER_NO = :2
            """, [item_id, g.member_no])
            row = cur.fetchone()
            if not row:
                abort(404)

            ingredient = {
                "item_id": row[0],
                "name": row[1],
                "quantity": row[2],
                "purchase_date": to_date(row[3]),
                "expiration_date": to_date(row[4]),
                "is_exp_unknown": row[5],
            }

        return render_template('IngredientForm.html', ingredient=ingredient,
                               action=url_for('ingredient_update', item_id=item_id))
    except Exception as e:
        print("수정 폼 오류:", e)
        return f"수정 폼 오류: {e}"

@app.route('/ingredients/<int:item_id>/update', methods=['POST'])
def ingredient_update(item_id):
    if not g.member_no:
        return redirect(url_for('login_page'))

    name = (request.form.get('name') or '').strip()
    quantity = (request.form.get('quantity') or '').strip()
    purchase_date = request.form.get('purchase_date')
    expiration_date = request.form.get('expiration_date')
    is_unknown = 'Y' if request.form.get('is_expiration_unknown') == 'on' else 'N'

    if is_unknown == 'Y':
        expiration_date = None

    if not name or not purchase_date:
        return "재료명과 구매일은 필수입니다.", 400

    try:
        with get_db_connection() as conn, conn.cursor() as cur:
            # INGREDIENTS 보장
            cur.execute("SELECT INGREDIENT_ID FROM INGREDIENTS WHERE NAME = :1", [name])
            row = cur.fetchone()
            if row:
                ingredient_id = int(row[0])
            else:
                out_id = cur.var(oracledb.NUMBER)
                cur.execute("""
                    INSERT INTO INGREDIENTS (NAME)
                    VALUES (:1)
                    RETURNING INGREDIENT_ID INTO :2
                """, [name, out_id])
                val = out_id.getvalue()
                if isinstance(val, list):
                    val = val[0]
                ingredient_id = int(val)

            # 업데이트
            if expiration_date:
                cur.execute("""
                    UPDATE REFRIGERATOR_ITEMS
                    SET INGREDIENT_ID = :1,
                        QUANTITY = :2,
                        PURCHASE_DATE = TO_DATE(:3, 'YYYY-MM-DD'),
                        EXPIRATION_DATE = TO_DATE(:4, 'YYYY-MM-DD'),
                        IS_EXPIRATION_UNKNOWN = :5
                    WHERE ITEM_ID = :6 AND MEMBER_NO = :7
                """, [ingredient_id, quantity, purchase_date, expiration_date, is_unknown, item_id, g.member_no])
            else:
                cur.execute("""
                    UPDATE REFRIGERATOR_ITEMS
                    SET INGREDIENT_ID = :1,
                        QUANTITY = :2,
                        PURCHASE_DATE = TO_DATE(:3, 'YYYY-MM-DD'),
                        EXPIRATION_DATE = NULL,
                        IS_EXPIRATION_UNKNOWN = :4
                    WHERE ITEM_ID = :5 AND MEMBER_NO = :6
                """, [ingredient_id, quantity, purchase_date, is_unknown, item_id, g.member_no])

            if cur.rowcount == 0:
                abort(404)

            conn.commit()
        return redirect(url_for('refrigerator_page'))
    except Exception as e:
        print("수정 오류:", e)
        return f"수정 오류: {e}"

@app.route('/ingredients/<int:item_id>/delete', methods=['POST'])
def ingredient_delete(item_id):
    if not g.member_no:
        return redirect(url_for('login_page'))

    try:
        with get_db_connection() as conn, conn.cursor() as cur:
            cur.execute("""
                DELETE FROM REFRIGERATOR_ITEMS
                WHERE ITEM_ID = :1 AND MEMBER_NO = :2
            """, [item_id, g.member_no])
            if cur.rowcount == 0:
                abort(404)
            conn.commit()
        return redirect(url_for('refrigerator_page'))
    except Exception as e:
        print("삭제 오류:", e)
        return f"삭제 오류: {e}"

# -----------------------------
#  내 레시피 목록 페이지
# -----------------------------
@app.route('/my_recipes')
def my_recipes_page():
    if 'member_no' not in session:
        return redirect(url_for('login_page'))

    conn = get_db_connection(); cur = conn.cursor()
    cur.execute("""
        SELECT RECIPE_ID, TITLE, DESCRIPTION, MAIN_IMAGE_URL
        FROM RECIPES
        WHERE AUTHOR_NO = :1
        ORDER BY RECIPE_ID DESC
    """, [session['member_no']])
    rows = cur.fetchall()
    cur.close(); conn.close()

    my_recipes = [
        {"id": r[0], "title": r[1], "description": r[2], "image": r[3]}
        for r in rows
    ]
    return render_template('my_recipes.html', my_recipes=my_recipes)


# -----------------------------
# 3. 레시피 등록 페이지 (GET)
# -----------------------------
@app.route('/recipe/add')
def recipe_add_page():
    if 'member_no' not in session:
        flash("로그인이 필요합니다.")
        return redirect(url_for('login_page'))

    ingredients_list = []
    try:
        conn = get_db_connection()
        if conn:
            cur = conn.cursor()
            cur.execute("SELECT ingredient_id, name FROM ingredients ORDER BY name")
            ingredients_list = cur.fetchall()
            cur.close()
            conn.close()
    except Exception as e:
        print(f"DB 조회 오류: {e}")
        flash("재료 목록을 불러오는 중 오류가 발생했습니다.")

    return render_template('recipe_add.html', ingredients_list=ingredients_list)


# -----------------------------
# 4. 레시피 등록 처리 (POST)
# -----------------------------
@app.route('/recipe/add', methods=['POST'])
def recipe_add_submit():
    if 'member_no' not in session:
        flash("로그인이 필요합니다.")
        return redirect(url_for('login_page'))

    member_no = session['member_no']

    # 폼 값
    title = request.form.get('title')
    description = request.form.get('description')
    image_url = request.form.get('image_url')

    # ✅ 이름/수량 리스트로 변경
    ingredient_names = [s.strip() for s in request.form.getlist('ingredient_name')]
    ingredient_quantities = [s.strip() for s in request.form.getlist('ingredient_quantity')]
    step_instructions = [s.strip() for s in request.form.getlist('step_instruction')]

    # 필수 체크
    has_any_ingredient = any(n for n in ingredient_names)
    has_any_step = any(s for s in step_instructions)
    if not title or not has_any_ingredient or not has_any_step:
        flash("필수 항목(제목, 재료, 순서)이 누락되었습니다.")
        return redirect(url_for('recipe_add_page'))

    conn = get_db_connection()
    if not conn:
        flash("DB 연결 실패")
        return redirect(url_for('my_recipes_page'))

    cur = conn.cursor()
    try:
        # 1) 레시피 INSERT + ID 받기 (IDENTITY 컬럼)
        new_id_var = cur.var(oracledb.NUMBER)
        cur.execute("""
            INSERT INTO RECIPES (AUTHOR_NO, TITLE, DESCRIPTION, MAIN_IMAGE_URL, CREATED_AT)
            VALUES (:1, :2, :3, :4, SYSTIMESTAMP)
            RETURNING RECIPE_ID INTO :5
        """, [member_no, title, description, image_url, new_id_var])
        val = new_id_var.getvalue()
        recipe_id = int(val[0] if isinstance(val, list) else val)

        # 2) 재료 처리: 이름 → INGREDIENTS에서 ID 찾기, 없으면 생성
        for i, name in enumerate(ingredient_names):
            if not name:
                continue
            qty = ingredient_quantities[i] if i < len(ingredient_quantities) and ingredient_quantities[i] else '적당량'

            # 먼저 존재 여부 확인
            cur.execute("SELECT INGREDIENT_ID FROM INGREDIENTS WHERE NAME = :1", [name])
            row = cur.fetchone()
            if row:
                ing_id = int(row[0])
            else:
                # 없으면 생성하고 ID 받기
                new_ing_id = cur.var(oracledb.NUMBER)
                cur.execute("""
                    INSERT INTO INGREDIENTS (NAME)
                    VALUES (:1)
                    RETURNING INGREDIENT_ID INTO :2
                """, [name, new_ing_id])
                v = new_ing_id.getvalue()
                ing_id = int(v[0] if isinstance(v, list) else v)

            # 연결 테이블 저장
            cur.execute("""
                INSERT INTO RECIPE_INGREDIENTS (RECIPE_ID, INGREDIENT_ID, REQUIRED_QUANTITY)
                VALUES (:1, :2, :3)
            """, [recipe_id, ing_id, qty])

        # 3) 조리 순서 저장
        step_data = []
        for idx, instr in enumerate(step_instructions, start=1):
            if not instr:
                continue
            step_data.append((recipe_id, idx, instr, None))
        if step_data:
            cur.executemany("""
                INSERT INTO RECIPE_STEPS (RECIPE_ID, STEP_NUMBER, INSTRUCTION, IMAGE_URL)
                VALUES (:1, :2, :3, :4)
            """, step_data)

        # 4) ✅ 레시피 등록 보상: 숙련도(POINTS) +100
        POINTS_FOR_RECIPE_ADD = 50
        cur.execute("""
            UPDATE MEMBERS
               SET POINTS = NVL(POINTS, 0) + :1
             WHERE MEMBER_NO = :2
        """, [POINTS_FOR_RECIPE_ADD, member_no])

        # (선택) 세션 즉시 반영
        try:
            session['points'] = (session.get('points') or 0) + POINTS_FOR_RECIPE_ADD
        except Exception:
            pass

        add_notification(member_no, f"새 레시피가 성공적으로 등록되었습니다! (+{POINTS_FOR_RECIPE_ADD}P)")


        conn.commit()
        flash(f"새 레시피가 성공적으로 등록되었습니다! (+{POINTS_FOR_RECIPE_ADD}P)")
        return redirect(url_for('my_recipes_page'))

    except oracledb.DatabaseError as e:
        conn.rollback()
        print("\n[RECIPE ADD ERROR]\n", e, "\n")
        flash(f"DB 오류 발생: {e}")
        return redirect(url_for('recipe_add_page'))
    finally:
        cur.close()
        conn.close()


# -----------------------------------------------
# 레시피 리뷰/별점 등록
# [수정] 모든 바인드 변수 이름 앞에 'p_' 접두사 추가
# -----------------------------------------------
@app.route('/recipe/<int:recipe_id>/review', methods=['POST'])
def submit_review(recipe_id):
    if 'member_no' not in session:
        flash("로그인이 필요합니다.")
        return redirect(url_for('login_page'))

    member_no = session['member_no']
    rating = request.form.get('rating')
    comment_text = request.form.get('comment_text', '').strip()

    if not rating or not comment_text:
        flash("별점과 리뷰 내용을 입력해주세요.")
        return redirect(url_for('add_review_page', recipe_id=recipe_id))

    conn = get_db_connection()
    cur = conn.cursor()

    try:
        rating_val = float(rating)

        # 1) 리뷰 INSERT
        cur.execute("""
            INSERT INTO REVIEWS (RECIPE_ID, MEMBER_NO, RATING, COMMENT_TEXT, CREATED_AT)
            VALUES (:1, :2, :3, :4, SYSTIMESTAMP)
        """, [recipe_id, member_no, rating_val, comment_text])

        # 2) 평균 평점 업데이트
        cur.execute("""
            UPDATE RECIPES
               SET AVG_RATING = (SELECT ROUND(AVG(RATING),1) FROM REVIEWS WHERE RECIPE_ID = :1)
             WHERE RECIPE_ID = :2
        """, [recipe_id, recipe_id])

        # 3) ✅ 숙련도(POINTS) +50
        POINTS_FOR_REVIEW = 50
        cur.execute("""
            UPDATE MEMBERS
               SET POINTS = NVL(POINTS, 0) + :1
             WHERE MEMBER_NO = :2
        """, [POINTS_FOR_REVIEW, member_no])

        # (선택) 세션 즉시 반영
        try:
            session['points'] = (session.get('points') or 0) + POINTS_FOR_REVIEW
        except Exception:
            pass
        add_notification(member_no, "리뷰가 등록되었습니다! (+50P)")
        conn.commit()
        flash(f"리뷰가 등록되었습니다! (+{POINTS_FOR_REVIEW}P)", "main_notice")
        return redirect(url_for('recipe_detail', recipe_id=recipe_id))

    except Exception as e:
        conn.rollback()
        flash(f"리뷰 저장 오류 발생: {e}")
        print("REVIEW INSERT ERROR:", e)
        return redirect(url_for('add_review_page', recipe_id=recipe_id))
    finally:
        cur.close()
        conn.close()


# -----------------------------
# 리뷰 작성 페이지 (GET)
# -----------------------------
@app.route('/recipe/<int:recipe_id>/review/add')
def add_review_page(recipe_id):
    if 'member_no' not in session:
        flash("로그인이 필요합니다.")
        return redirect(url_for('login_page'))

    conn = get_db_connection()
    cur = conn.cursor()
    # 🔥 이름 바인드 -> 순서 바인드로 통일
    cur.execute("SELECT TITLE FROM RECIPES WHERE RECIPE_ID = :1", [recipe_id])
    row = cur.fetchone()
    cur.close()
    conn.close()

    if not row:
        flash("존재하지 않는 레시피입니다.")
        return redirect(url_for('recipes_page'))

    return render_template('recipe_review_add.html', recipe_id=recipe_id, recipe_title=row[0])



# -----------------------------
# 레시피 삭제
# -----------------------------
@app.route('/recipe/delete/<int:recipe_id>', methods=['POST'])
def delete_recipe(recipe_id):
    if 'member_no' not in session:
        if request.is_json:
            return jsonify({"success": False, "message": "로그인이 필요합니다."})
        flash("로그인이 필요합니다.")
        return redirect(url_for('login_page'))

    member_no = session['member_no']

    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("""
            DELETE FROM RECIPES
            WHERE RECIPE_ID = :id AND AUTHOR_NO = :author
        """, {"id": recipe_id, "author": member_no})

        if cur.rowcount == 0:
            if request.is_json:
                return jsonify({"success": False, "message": "삭제 권한이 없거나 존재하지 않는 레시피입니다."})
            flash("삭제 권한이 없거나 존재하지 않는 레시피입니다.")
            return redirect(url_for('recipes_page'))

        conn.commit()
        if request.is_json:
            return jsonify({"success": True})
        flash("레시피가 삭제되었습니다.")
        return redirect(url_for('recipes_page'))

    except Exception as e:
        conn.rollback()
        if request.is_json:
            return jsonify({"success": False, "message": str(e)})
        flash(f"삭제 중 오류가 발생했습니다: {e}")
        return redirect(url_for('recipes_page'))
    finally:
        cur.close()
        conn.close()

#-------------
# 수정페이지 열기
# --------------
@app.route('/recipe/edit/<int:recipe_id>')
def recipe_edit_page(recipe_id):
    if 'member_no' not in session:
        flash("로그인이 필요합니다.")
        return redirect(url_for('login_page'))

    member_no = session['member_no']
    conn = get_db_connection()
    if not conn:
        flash("DB 연결 실패")
        return redirect(url_for('my_recipes_page'))

    cur = conn.cursor()
    try:
        # 본문
        cur.execute("""
            SELECT RECIPE_ID, TITLE, DESCRIPTION, MAIN_IMAGE_URL
            FROM RECIPES
            WHERE RECIPE_ID = :1 AND AUTHOR_NO = :2
        """, [recipe_id, member_no])
        row = cur.fetchone()
        if not row:
            flash("수정 권한이 없거나 존재하지 않는 레시피입니다.")
            return redirect(url_for('my_recipes_page'))

        recipe = {
            "id": row[0],
            "title": row[1],
            "description": row[2],
            "image_url": row[3]
        }

        # 재료 (이름 + 수량)
        cur.execute("""
            SELECT i.NAME, ri.REQUIRED_QUANTITY
            FROM RECIPE_INGREDIENTS ri
            JOIN INGREDIENTS i ON i.INGREDIENT_ID = ri.INGREDIENT_ID
            WHERE ri.RECIPE_ID = :1
            ORDER BY i.NAME
        """, [recipe_id])
        recipe_ingredients = [{"name": r[0], "quantity": r[1]} for r in cur.fetchall()]

        # 조리 순서
        cur.execute("""
            SELECT STEP_NUMBER, INSTRUCTION
            FROM RECIPE_STEPS
            WHERE RECIPE_ID = :1
            ORDER BY STEP_NUMBER
        """, [recipe_id])
        recipe_steps = [{"number": r[0], "instruction": r[1]} for r in cur.fetchall()]

    finally:
        cur.close()
        conn.close()


# -----------------------------
# 사용자 프로필 페이지
# -----------------------------
@app.route('/profile/<int:member_id>')
def profile_page(member_id):
    conn = get_db_connection()
    if not conn:
        flash("DB 연결 실패")
        return redirect(url_for('main_page'))

    user_info = None
    recipes = []
    try:
        cur = conn.cursor()
        cur.execute("""
            SELECT MEMBER_NO, NAME, USER_ID, NVL(POINTS,0) AS POINTS
              FROM MEMBERS
             WHERE MEMBER_NO = :1
        """, [member_id])
        row = cur.fetchone()
        if not row:
            flash("존재하지 않는 사용자입니다.")
            return redirect(url_for('main_page'))

        user_info = {
            "id": row[0],
            "name": row[1],
            "user_id": row[2],
            "points": int(row[3] or 0),
            "profile_image": get_profile_image_url(member_id)
        }

        cur.execute("""
            SELECT RECIPE_ID, TITLE, DESCRIPTION, MAIN_IMAGE_URL, NVL(AVG_RATING,0)
              FROM RECIPES
             WHERE AUTHOR_NO = :1
             ORDER BY RECIPE_ID DESC
        """, [member_id])
        for r in cur.fetchall():
            recipes.append({
                "id": r[0],
                "title": r[1],
                "description": r[2],
                "image": r[3] or url_for('static', filename='images/recipe_default.png'),
                "rating": float(r[4] or 0)
            })
    except Exception as e:
        print("Profile load error:", e)
        flash("프로필을 불러오는 중 오류가 발생했습니다.")
        return redirect(url_for('main_page'))
    finally:
        if cur: cur.close()
        if conn: conn.close()

    return render_template('profile.html',
                           profile=user_info,
                           recipes=recipes)

@app.route('/profile/<int:member_id>/photo', methods=['POST'])
def update_profile_photo(member_id):
    if 'member_no' not in session or session['member_no'] != member_id:
        abort(403)
    file = request.files.get('photo')
    if not file or not file.filename:
        flash("업로드할 이미지를 선택하세요.")
        return redirect(url_for('profile_page', member_id=member_id))
    ext = file.filename.rsplit('.', 1)[-1].lower() if '.' in file.filename else ''
    if ext not in ALLOWED_PROFILE_EXT:
        flash("이미지 형식은 png/jpg/jpeg/gif/webp만 지원합니다.")
        return redirect(url_for('profile_page', member_id=member_id))

    os.makedirs(PROFILE_UPLOAD_DIR, exist_ok=True)
    save_path = os.path.join(PROFILE_UPLOAD_DIR, f"profile_{member_id}.{ext}")
    # 다른 확장자로 저장된 이전 파일은 삭제
    for e in ALLOWED_PROFILE_EXT:
        old = os.path.join(PROFILE_UPLOAD_DIR, f"profile_{member_id}.{e}")
        if os.path.exists(old) and old != save_path:
            try:
                os.remove(old)
            except OSError:
                pass
    file.save(save_path)
    flash("프로필 이미지가 업데이트되었습니다.")
    # 메인에서 올리는 사용성: referer가 main이면 그대로 메인으로
    ref = request.referrer or ""
    if "/main" in ref:
        return redirect(url_for('main_page'))
    return redirect(url_for('profile_page', member_id=member_id))

    return render_template('recipe_edit.html',
                           recipe=recipe,
                           recipe_ingredients=recipe_ingredients,
                           recipe_steps=recipe_steps)

#--------------------------
# 수정 저장
#--------------------------
@app.route('/recipe/edit/<int:recipe_id>', methods=['POST'])
def recipe_edit_submit(recipe_id):
    if 'member_no' not in session:
        flash("로그인이 필요합니다.")
        return redirect(url_for('login_page'))

    member_no = session['member_no']
    title = request.form.get('title', '').strip()
    description = request.form.get('description', '').strip()
    image_url = request.form.get('image_url', '').strip()

    ing_names = [s.strip() for s in request.form.getlist('ingredient_name')]
    ing_qtys  = [s.strip() for s in request.form.getlist('ingredient_quantity')]
    steps     = [s.strip() for s in request.form.getlist('step_instruction')]

    if not title:
        flash("제목은 필수입니다.")
        return redirect(url_for('recipe_edit_page', recipe_id=recipe_id))

    conn = get_db_connection()
    if not conn:
        flash("DB 연결 실패")
        return redirect(url_for('recipe_edit_page', recipe_id=recipe_id))

    cur = conn.cursor()
    try:
        # 권한 체크
        cur.execute("SELECT 1 FROM RECIPES WHERE RECIPE_ID = :1 AND AUTHOR_NO = :2", [recipe_id, member_no])
        if not cur.fetchone():
            flash("수정 권한이 없습니다.")
            return redirect(url_for('my_recipes_page'))

        # 1) 메인 정보 업데이트
        cur.execute("""
            UPDATE RECIPES
            SET TITLE = :1, DESCRIPTION = :2, MAIN_IMAGE_URL = :3
            WHERE RECIPE_ID = :4
        """, [title, description, image_url, recipe_id])

        # 2) 기존 재료/순서 삭제
        cur.execute("DELETE FROM RECIPE_INGREDIENTS WHERE RECIPE_ID = :1", [recipe_id])
        cur.execute("DELETE FROM RECIPE_STEPS      WHERE RECIPE_ID = :1", [recipe_id])

        # 3) 재료 다시 저장 (이름→ID upsert)
        for i, name in enumerate(ing_names):
            if not name:
                continue
            qty = ing_qtys[i] if i < len(ing_qtys) and ing_qtys[i] else '적당량'

            cur.execute("SELECT INGREDIENT_ID FROM INGREDIENTS WHERE NAME = :1", [name])
            row = cur.fetchone()
            if row:
                ing_id = int(row[0])
            else:
                new_ing_id = cur.var(oracledb.NUMBER)
                cur.execute("""
                    INSERT INTO INGREDIENTS (NAME)
                    VALUES (:1)
                    RETURNING INGREDIENT_ID INTO :2
                """, [name, new_ing_id])
                v = new_ing_id.getvalue()
                ing_id = int(v[0] if isinstance(v, list) else v)

            cur.execute("""
                INSERT INTO RECIPE_INGREDIENTS (RECIPE_ID, INGREDIENT_ID, REQUIRED_QUANTITY)
                VALUES (:1, :2, :3)
            """, [recipe_id, ing_id, qty])

        # 4) 조리 순서 다시 저장
        step_rows = []
        order = 1
        for s in steps:
            if not s:
                continue
            step_rows.append((recipe_id, order, s, None))
            order += 1
        if step_rows:
            cur.executemany("""
                INSERT INTO RECIPE_STEPS (RECIPE_ID, STEP_NUMBER, INSTRUCTION, IMAGE_URL)
                VALUES (:1, :2, :3, :4)
            """, step_rows)

        conn.commit()
        flash("레시피가 수정되었습니다!")
        return redirect(url_for('recipe_detail', recipe_id=recipe_id))

    except Exception as e:
        conn.rollback()
        flash(f"오류 발생: {e}")
        return redirect(url_for('recipe_edit_page', recipe_id=recipe_id))
    finally:
        cur.close()
        conn.close()
# -----------------------------
# 로그아웃
# -----------------------------
@app.route('/logout')
def logout():
    session.clear()
    flash("로그아웃되었습니다.")
    return redirect(url_for('login_page'))

# 마트와 연동하기 부분 --------------------------------
@app.route('/martconnect')
def mart_connect_page():
    if not g.member_no:
        flash("로그인이 필요합니다.")
        return redirect(url_for('login_page'))

    conn = get_db_connection()
    if not conn:
        flash("DB 연결 실패")
        return redirect(url_for('main_page'))

    try:
        cur = conn.cursor()
        # 마트 목록 + 현재 회원의 연동 상태
        cur.execute("""
            SELECT m.MART_ID, m.NAME, m.LOCATION, m.STATUS,
                   CASE WHEN mi.MART_NAME IS NOT NULL THEN 1 ELSE 0 END AS IS_CONNECTED
              FROM MARTS m
              LEFT JOIN (
                    SELECT MART_NAME
                      FROM MART_INTEGRATIONS
                     WHERE MEMBER_NO = :member_no
                       AND IS_ACTIVE = 'Y'
                ) mi
                ON mi.MART_NAME = m.NAME
             ORDER BY m.NAME
        """, {"member_no": g.member_no})
        mart_list = []
        for row in cur.fetchall():
            mart_list.append({
                "mart_id": row[0],
                "name": row[1],
                "location": row[2],
                "status": row[3],
                "connected": bool(row[4]),
            })
        return render_template('martconnect.html', mart_list=mart_list)
    except oracledb.DatabaseError as e:
        flash(f"마트 목록 로드 중 오류 발생: {e}")
        print(f"Mart Connect DB Error: {e}")
        return redirect(url_for('main_page'))
    finally:
        if cur: cur.close()
        if conn: conn.close()

# -----------------------------
# 마트 로그인 페이지
# -----------------------------
@app.route('/martlogin/<int:mart_id>', methods=['POST'])
def mart_login_api(mart_id):
    if 'member_no' not in session:
        return {"success": False, "message": "로그인이 필요합니다."}

    data = request.get_json()
    mart_user_id = data.get('mart_user_id', '').strip()
    mart_password = data.get('mart_password', '').strip()

    if not mart_user_id or not mart_password:
        return {"success": False, "message": "아이디와 비밀번호를 모두 입력해주세요."}

    conn = get_db_connection()
    if not conn:
        return {"success": False, "message": "DB 연결 실패"}

    cur = conn.cursor()
    try:
        cur.execute("SELECT NAME FROM MARTS WHERE MART_ID = :1", [mart_id])
        mart_row = cur.fetchone()
        if not mart_row:
            return {"success": False, "message": "존재하지 않는 마트입니다."}
        mart_name = mart_row[0]

        # 기존 연동이 있으면 활성화/업데이트, 없으면 생성
        cur.execute("""
            SELECT INTEGRATION_ID
              FROM MART_INTEGRATIONS
             WHERE MEMBER_NO = :member_no
               AND MART_NAME = :mart_name
             ORDER BY UPDATED_AT DESC
        """, {"member_no": session['member_no'], "mart_name": mart_name})
        row = cur.fetchone()
        if row:
            cur.execute("""
                UPDATE MART_INTEGRATIONS
                   SET MART_USER_ID = :user_id,
                       API_TOKEN = :token,
                       IS_ACTIVE = 'Y',
                       UPDATED_AT = SYSTIMESTAMP
                 WHERE INTEGRATION_ID = :integration_id
            """, {
                "user_id": mart_user_id,
                "token": "샘플토큰",
                "integration_id": row[0]
            })
        else:
            cur.execute("""
                INSERT INTO MART_INTEGRATIONS (MEMBER_NO, MART_NAME, MART_USER_ID, API_TOKEN, IS_ACTIVE)
                VALUES (:member_no, :mart_name, :user_id, :token, 'Y')
            """, {
                "member_no": session['member_no'],
                "mart_name": mart_name,
                "user_id": mart_user_id,
                "token": "샘플토큰"
            })
        conn.commit()
        return {"success": True, "message": "연동이 완료되었습니다!"}
    except oracledb.DatabaseError as e:
        conn.rollback()
        return {"success": False, "message": f"연동 중 오류 발생: {e}"}
    finally:
        cur.close()
        conn.close()

@app.route('/martlogin/<int:mart_id>/disconnect', methods=['POST'])
def mart_disconnect_api(mart_id):
    if 'member_no' not in session:
        return {"success": False, "message": "로그인이 필요합니다."}

    conn = get_db_connection()
    if not conn:
        return {"success": False, "message": "DB 연결 실패"}

    cur = conn.cursor()
    try:
        cur.execute("SELECT NAME FROM MARTS WHERE MART_ID = :1", [mart_id])
        mart_row = cur.fetchone()
        if not mart_row:
            return {"success": False, "message": "존재하지 않는 마트입니다."}
        mart_name = mart_row[0]

        cur.execute("""
            UPDATE MART_INTEGRATIONS
               SET IS_ACTIVE = 'N',
                   UPDATED_AT = SYSTIMESTAMP
             WHERE MEMBER_NO = :member_no
               AND MART_NAME = :mart_name
               AND IS_ACTIVE = 'Y'
        """, {"member_no": session['member_no'], "mart_name": mart_name})
        conn.commit()
        return {"success": True, "message": "연동이 해제되었습니다."}
    except oracledb.DatabaseError as e:
        conn.rollback()
        return {"success": False, "message": f"연동 해제 중 오류 발생: {e}"}
    finally:
        cur.close()
        conn.close()

# -----------------------------
# 냉장고 다건 추가 API (마트 구매 → 냉장고)
# -----------------------------
@app.route('/refrigerator/bulk_add', methods=['POST'])
def refrigerator_bulk_add():
    if not g.member_no:
        return jsonify({"success": False, "message": "로그인이 필요합니다."}), 401

    payload = request.get_json(silent=True) or {}
    items = payload.get('items') or []
    if not items:
        return jsonify({"success": False, "message": "추가할 항목이 없습니다."}), 400

    try:
        with get_db_connection() as conn, conn.cursor() as cur:
            for item in items:
                name = (item.get('name') or '').strip()
                if not name:
                    continue
                quantity = (item.get('quantity') or '').strip() or '1개'
                purchase_date = (item.get('purchase_date') or date.today().strftime('%Y-%m-%d'))
                expiration_date = (item.get('expiration_date') or '').strip()
                is_unknown = 'N' if expiration_date else 'Y'

                # INGREDIENTS upsert (by name)
                cur.execute("SELECT INGREDIENT_ID FROM INGREDIENTS WHERE NAME = :1", [name])
                row = cur.fetchone()
                if row:
                    ingredient_id = int(row[0])
                else:
                    out_id = cur.var(oracledb.NUMBER)
                    cur.execute("""
                        INSERT INTO INGREDIENTS (NAME)
                        VALUES (:1)
                        RETURNING INGREDIENT_ID INTO :2
                    """, [name, out_id])
                    val = out_id.getvalue(); ingredient_id = int(val[0] if isinstance(val, list) else val)

                # Insert refrigerator item
                if expiration_date:
                    cur.execute("""
                        INSERT INTO REFRIGERATOR_ITEMS
                          (MEMBER_NO, INGREDIENT_ID, QUANTITY, PURCHASE_DATE,
                           EXPIRATION_DATE, IS_EXPIRATION_UNKNOWN)
                        VALUES
                          (:1, :2, :3, TO_DATE(:4, 'YYYY-MM-DD'),
                           TO_DATE(:5, 'YYYY-MM-DD'), :6)
                    """, [g.member_no, ingredient_id, quantity, purchase_date, expiration_date, is_unknown])
                else:
                    cur.execute("""
                        INSERT INTO REFRIGERATOR_ITEMS
                          (MEMBER_NO, INGREDIENT_ID, QUANTITY, PURCHASE_DATE,
                           EXPIRATION_DATE, IS_EXPIRATION_UNKNOWN)
                        VALUES
                          (:1, :2, :3, TO_DATE(:4, 'YYYY-MM-DD'),
                           NULL, :5)
                    """, [g.member_no, ingredient_id, quantity, purchase_date, is_unknown])

            conn.commit()
        return jsonify({"success": True})
    except Exception as e:
        print("Bulk add error:", e)
        return jsonify({"success": False, "message": str(e) }), 500

@app.route('/martpurchase/<int:mart_id>')
def mart_purchase_page(mart_id):
    if 'member_no' not in session:
        flash("로그인이 필요합니다.")
        return redirect(url_for('login_page'))

    # DB에서 mart_id에 해당하는 마트 정보 가져오기 (선택 사항)
    conn = get_db_connection()
    mart_name = "마트 이름"  # 기본값
    try:
        cur = conn.cursor()
        cur.execute("SELECT NAME FROM MARTS WHERE MART_ID = :mart_id", {"mart_id": mart_id})
        row = cur.fetchone()
        if row:
            mart_name = row[0]
    except oracledb.DatabaseError as e:
        print(f"DB Error: {e}")
    finally:
        if cur: cur.close()
        if conn: conn.close()

    purchase_date_str = date.today().strftime('%Y. %m. %d.')
    return render_template('mart_purchase.html', mart_name=mart_name, purchase_date_str=purchase_date_str)

# ----------------------------------------------------
# [신규] 선택한 재료를 사용하는 레시피 추천
#   /recipes/by_ingredients?ids=1,2,3&mode=any|all
#   mode=any: 선택 재료 중 하나라도 포함
#   mode=all: 선택 재료를 모두 포함(교집합)
# ----------------------------------------------------
@app.route('/recipes/by_ingredients')
def recipes_by_ingredients():
    ids_str = (request.args.get('ids') or '').strip()
    mode = (request.args.get('mode') or 'any').lower()
    selected_sorts = request.args.getlist('sort')
    member_no = g.member_no or -1
    expand = (request.args.get('expand') or '0').lower() in ('1', 'true', 'yes')

    try:
        ing_ids = [int(x) for x in ids_str.split(',') if x.strip().isdigit()]
    except:
        ing_ids = []
    if not ing_ids:
        flash("선택한 재료가 없습니다.")
        return redirect(url_for('refrigerator_page'))

    # 선택 재료 이름 목록 (ID가 달라도 이름으로 매칭하기 위함)
    ing_names = []
    try:
        with get_db_connection() as _conn, _conn.cursor() as _cur:
            ph = ",".join([f":{i+1}" for i in range(len(ing_ids))])
            _cur.execute(f"SELECT NAME FROM INGREDIENTS WHERE INGREDIENT_ID IN ({ph})", ing_ids)
            ing_names = [ (row[0] or '').strip().lower() for row in _cur.fetchall() if row and row[0] ]
            ing_names = [n for n in ing_names if n]
    except Exception:
        pass

    # --- 확장 매칭(옵션): expand=1 일 때만 유사 이름 재료까지 포함 ---
    if expand:
        try:
            with get_db_connection() as _conn, _conn.cursor() as _cur:
                base_ph = ",".join([f":{i+1}" for i in range(len(ing_ids))])
                _cur.execute(f"SELECT NAME FROM INGREDIENTS WHERE INGREDIENT_ID IN ({base_ph})", ing_ids)
                base_names = [ (row[0] or '').strip() for row in _cur.fetchall() if row and row[0] ]

                patterns = [ f"%{nm}%" for nm in base_names if nm ]
                if patterns:
                    conds = " OR ".join([ f"LOWER(NAME) LIKE LOWER(:p{i+1})" for i in range(len(patterns)) ])
                    params = { f"p{i+1}": patterns[i] for i in range(len(patterns)) }
                    _cur.execute(f"SELECT INGREDIENT_ID FROM INGREDIENTS WHERE {conds}", params)
                    similar_ids = [ int(r[0]) for r in _cur.fetchall() ]
                    ing_ids = sorted({ *ing_ids, *similar_ids })
                    ing_names = sorted({ *ing_names, *[n.strip().lower() for n in base_names if n] })
        except Exception:
            # 확장 실패 시 원본 ID만 유지
            pass

    # Build named placeholders for ingredient IDs and names
    id_placeholders = ",".join([f":id{i}" for i in range(len(ing_ids))]) or "NULL"
    name_placeholders = ",".join([f":nm{i}" for i in range(len(ing_names))])

    where_parts = []
    if ing_ids:
        where_parts.append(f"ri.INGREDIENT_ID IN ({id_placeholders})")
    if ing_names:
        where_parts.append(f"LOWER(i.NAME) IN ({name_placeholders})")
    where_clause = " OR ".join(where_parts) if where_parts else "1=0"

    sql = f"""
        SELECT
            r.RECIPE_ID,
            r.TITLE,
            r.DESCRIPTION,
            r.MAIN_IMAGE_URL,
            m.NAME AS AUTHOR_NAME,
            NVL(r.AVG_RATING, 0) AS AVG_RATING,
            COUNT(DISTINCT ri.INGREDIENT_ID) AS MATCHED_COUNT_FOR_FILTER,
            (SELECT COUNT(*) FROM RECIPE_INGREDIENTS x WHERE x.RECIPE_ID = r.RECIPE_ID) AS TOTAL_INGS,
            (SELECT COUNT(*) FROM RECIPE_INGREDIENTS x
              WHERE x.RECIPE_ID = r.RECIPE_ID
                AND x.INGREDIENT_ID IN (
                    SELECT INGREDIENT_ID FROM REFRIGERATOR_ITEMS WHERE MEMBER_NO = :member_no
                )) AS OWN_MATCHED_INGS,
            (SELECT COUNT(*) FROM REVIEWS rv WHERE rv.RECIPE_ID = r.RECIPE_ID) AS REVIEW_COUNT
        FROM RECIPES r
        JOIN RECIPE_INGREDIENTS ri ON ri.RECIPE_ID = r.RECIPE_ID
        JOIN INGREDIENTS i ON i.INGREDIENT_ID = ri.INGREDIENT_ID
        LEFT JOIN MEMBERS m ON m.MEMBER_NO = r.AUTHOR_NO
        WHERE {where_clause}
        GROUP BY r.RECIPE_ID, r.TITLE, r.DESCRIPTION, r.MAIN_IMAGE_URL, m.NAME, r.AVG_RATING
    """

    params = {f"id{i}": ing_ids[i] for i in range(len(ing_ids))}
    params.update({f"nm{i}": ing_names[i] for i in range(len(ing_names))})
    params["member_no"] = member_no
    if mode == 'all':
        sql += " HAVING COUNT(DISTINCT ri.INGREDIENT_ID) >= :need_count"
        params["need_count"] = len(ing_ids)
    else:
        sql += " HAVING COUNT(DISTINCT ri.INGREDIENT_ID) >= 1"

    sql += """
        ORDER BY
            MATCHED_COUNT_FOR_FILTER DESC,
            NVL(r.AVG_RATING,0) DESC,
            r.RECIPE_ID DESC
    """

    conn = get_db_connection(); cur = conn.cursor()
    cur.execute(sql, params)
    rows = cur.fetchall()
    cur.close(); conn.close()

    recipes = []
    for r in rows:
        total = int(r[7] or 0)
        own_matched = int(r[8] or 0)
        feasibility = int(round((own_matched / total) * 100)) if total > 0 else 0

        recipes.append({
            "id": r[0],
            "title": r[1],
            "description": r[2],
            "image": r[3],
            "author": r[4] or 'Cook+Pick',
            "rating": float(r[5] or 0),
            "matched_count": int(r[6]),
            "feasibility": feasibility,        # ✅ 내 냉장고 기준 구현도
            "total_count": total,
            "own_matched": own_matched,
            "review_count": int(r[9] or 0),
        })

    # 정렬: 구현도(내재료) ↓, 별점 ↓, 리뷰수 ↓
    # 선택된 정렬 기준 적용 (없으면 기본 id desc 유지)
    if selected_sorts:
        key_fields = []
        if 'feasibility' in selected_sorts:
            key_fields.append('feasibility')
        if 'rating' in selected_sorts:
            key_fields.append('rating')
        if 'reviews' in selected_sorts:
            key_fields.append('review_count')
        if key_fields:
            recipes.sort(key=lambda x: tuple(x[k] for k in key_fields), reverse=True)

    return render_template('recipes.html',
                           recipes=recipes,
                           search_term=None,
                           selected_ing_ids=",".join(map(str, ing_ids)),
                           match_mode=mode,
                           selected_sorts=selected_sorts)

# ----------------------------------------------------
# 선택 재료 매칭 레시피 존재 여부 체크 (JSON)
#  - 반환: { count: number }
#  - 사용: 냉장고 화면에서 이동 전 존재 여부 확인
# ----------------------------------------------------
@app.route('/recipes/by_ingredients/check')
def recipes_by_ingredients_check():
    ids_str = (request.args.get('ids') or '').strip()
    mode = (request.args.get('mode') or 'any').lower()
    expand = (request.args.get('expand') or '0').lower() in ('1', 'true', 'yes')

    try:
        ing_ids = [int(x) for x in ids_str.split(',') if x.strip().isdigit()]
    except:
        ing_ids = []
    if not ing_ids:
        return jsonify({"count": 0})

    ing_names = []
    try:
        with get_db_connection() as _conn, _conn.cursor() as _cur:
            ph = ",".join([f":{i+1}" for i in range(len(ing_ids))])
            _cur.execute(f"SELECT NAME FROM INGREDIENTS WHERE INGREDIENT_ID IN ({ph})", ing_ids)
            ing_names = [ (row[0] or '').strip().lower() for row in _cur.fetchall() if row and row[0] ]
            ing_names = [n for n in ing_names if n]
    except Exception:
        pass

    # 확장 매칭 옵션 처리
    if expand:
        try:
            with get_db_connection() as _conn, _conn.cursor() as _cur:
                base_ph = ",".join([f":{i+1}" for i in range(len(ing_ids))])
                _cur.execute(f"SELECT NAME FROM INGREDIENTS WHERE INGREDIENT_ID IN ({base_ph})", ing_ids)
                base_names = [ (row[0] or '').strip() for row in _cur.fetchall() if row and row[0] ]
                patterns = [ f"%{nm}%" for nm in base_names if nm ]
                if patterns:
                    conds = " OR ".join([ f"LOWER(NAME) LIKE LOWER(:p{i+1})" for i in range(len(patterns)) ])
                    params = { f"p{i+1}": patterns[i] for i in range(len(patterns)) }
                    _cur.execute(f"SELECT INGREDIENT_ID FROM INGREDIENTS WHERE {conds}", params)
                    similar_ids = [ int(r[0]) for r in _cur.fetchall() ]
                    ing_ids = sorted({ *ing_ids, *similar_ids })
                    ing_names = sorted({ *ing_names, *[n.strip().lower() for n in base_names if n] })
        except Exception:
            pass

    placeholders = ",".join([f":id{i}" for i in range(len(ing_ids))]) or "NULL"
    name_placeholders = ",".join([f":nm{i}" for i in range(len(ing_names))])
    where_parts = []
    if ing_ids:
        where_parts.append(f"ri.INGREDIENT_ID IN ({placeholders})")
    if ing_names:
        where_parts.append(f"LOWER(i.NAME) IN ({name_placeholders})")
    where_clause = " OR ".join(where_parts) if where_parts else "1=0"

    # COUNT 쿼리 (member_no 비의존)
    sql = f"""
        SELECT COUNT(*) FROM (
            SELECT r.RECIPE_ID
            FROM RECIPES r
            JOIN RECIPE_INGREDIENTS ri ON ri.RECIPE_ID = r.RECIPE_ID
            JOIN INGREDIENTS i ON i.INGREDIENT_ID = ri.INGREDIENT_ID
            WHERE {where_clause}
            GROUP BY r.RECIPE_ID
    """
    params = {f"id{i}": ing_ids[i] for i in range(len(ing_ids))}
    params.update({f"nm{i}": ing_names[i] for i in range(len(ing_names))})
    if mode == 'all':
        sql += " HAVING COUNT(DISTINCT ri.INGREDIENT_ID) >= :need_count"
        params["need_count"] = len(ing_ids)
    else:
        sql += " HAVING COUNT(DISTINCT ri.INGREDIENT_ID) >= 1"
    sql += ") t"

    conn = get_db_connection(); cur = conn.cursor()
    cur.execute(sql, params)
    row = cur.fetchone()
    cur.close(); conn.close()

    count = int(row[0]) if row and row[0] is not None else 0
    return jsonify({"count": count})

@app.route('/notification/delete/<int:noti_id>', methods=['POST'])
def delete_notification(noti_id):
    if 'member_no' not in session:
        return redirect(url_for('login_page'))

    conn = get_db_connection()
    cur = conn.cursor()
    try:
        cur.execute("""
            DELETE FROM NOTIFICATIONS
            WHERE NOTI_ID = :1
              AND MEMBER_NO = :2
        """, [noti_id, session['member_no']])
        conn.commit()
    finally:
        cur.close()
        conn.close()

    return redirect(url_for('main_page'))



# -----------------------------
# 서버 실행
# -----------------------------
if __name__ == "__main__":
    # NLS_LANG 환경 변수 설정 (oracledb encoding 인자 대안)
    # 프로그램 시작 시 한 번만 설정
    if "NLS_LANG" not in os.environ:
         os.environ["NLS_LANG"] = "KOREAN_KOREA.KO16MSWIN949"
         print("NLS_LANG environment variable set.")

    app.run(debug=True)


