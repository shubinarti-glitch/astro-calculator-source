"""Localize public API errors without altering status codes or Russian defaults."""
TRANSLATIONS = {
    "На бесплатном тарифе можно сохранить одну карту. Подписка «Премиум» снимает ограничение и вернёт доступ ко всем сохранённым.": "The free plan allows one saved chart. A Premium subscription removes this limit and restores access to all saved charts.",
    "Пользователь с таким именем уже существует": "This username is already taken.",
    "Платёжная система не настроена (data/yookassa.json)": "The payment system is not configured.",
    "Такого местного времени не существовало из-за перевода часов. Укажите время до или после перехода.": "This local time did not exist because the clocks moved forward. Enter a time before or after the transition.",
    "Это местное время повторилось при переводе часов и неоднозначно. Укажите время с поправкой на первый или второй час.": "This local time occurred twice when the clocks changed and is ambiguous. Specify whether you mean the first or second occurrence.",
    "Требуется вход": "Please sign in.",
    "Сессия истекла, войдите снова": "Your session has expired. Please sign in again.",
    "Аккаунт заблокирован": "This account is blocked.",
    "Слишком много регистраций. Попробуйте позже.": "Too many registration attempts. Please try again later.",
    "Укажите почту — она нужна для чека об оплате и восстановления пароля": "Enter your email address. It is needed for payment receipts and password recovery.",
    "Необходимо принять политику и пользовательское соглашение": "Please accept the privacy policy and user agreement.",
    "Юридические документы обновились. Ознакомьтесь с ними повторно.": "The legal documents have been updated. Please review them again.",
    "Слишком много попыток входа. Попробуйте через несколько минут.": "Too many sign-in attempts. Please try again in a few minutes.",
    "Неверное имя пользователя или пароль": "Incorrect username or password.",
    "Слишком много запросов. Попробуйте позже.": "Too many requests. Please try again later.",
    "Слишком много запросов. Попробуйте через минуту.": "Too many requests. Please try again in a minute.",
    "Эта почта уже привязана к другому аккаунту": "This email address is already linked to another account.",
    "Ссылка недействительна или устарела": "This link is invalid or has expired.",
    "Отправка писем временно недоступна": "Email delivery is temporarily unavailable.",
    "Ссылка недействительна или устарела. Запросите сброс ещё раз.": "This link is invalid or has expired. Please request another password reset.",
    "Пользователь не найден": "User not found.",
    "Доступно по подписке «Премиум»": "A Premium subscription is required.",
    "Функция доступна по вашему тарифу": "Access to this feature depends on your plan.",
    "Платёжный сервис недоступен. Попробуйте позже.": "The payment service is unavailable. Please try again later.",
    "Текущий пароль неверен": "The current password is incorrect.",
    "Доступ только для администратора": "Administrator access is required.",
    "Нельзя удалить этого пользователя": "This user cannot be deleted.",
    "Неверный пароль или аккаунт нельзя удалить": "The password is incorrect or this account cannot be deleted.",
    "Индивидуальное право не найдено": "Individual access permission not found.",
    "Нельзя заблокировать этого пользователя": "This user cannot be blocked.",
    "Неизвестный ключ текста": "Unknown content key.",
    "Карта не найдена": "Chart not found.",
    "Нет доступных отчётов": "No reports are available.",
    "Не удалось рассчитать транзит дня": "The daily transit could not be calculated.",
    "Сначала подтвердите почту в кабинете": "Please verify your email address in your account first.",
    "Слишком много сообщений. Попробуйте позже.": "Too many messages. Please try again later.",
    "Необходимо принять актуальную политику обработки данных": "Please accept the current data processing policy.",
    "Пустое сообщение": "The message is empty.",
    "Слишком много событий. Попробуйте позже.": "Too many events. Please try again later.",
    "Не удалось выполнить расчёт. Проверьте корректность введённых данных.": "The calculation could not be completed. Please check the data you entered.",
    "Сервис геокодинга временно недоступен. Попробуйте позже или введите координаты вручную.": "The location search service is temporarily unavailable. Try again later or enter coordinates manually.",
    "Сервис геокодинга временно недоступен.": "The location search service is temporarily unavailable.",
    "Неизвестный часовой пояс": "Unknown time zone.",
    "Слишком большой запрос": "The request is too large.",
    "Некорректный адрес почты": "Invalid email address.",
    "Дата конца должна быть позже даты начала": "The end date must be later than the start date.",
    "Дата конца периода должна быть позже даты начала": "The end date must be later than the start date.",
    "Период прогноза слишком большой — максимум 3 года": "The forecast period is too long. The maximum is three years.",
    "Период слишком большой — максимум 2 года": "The period is too long. The maximum is two years.",
    "Конец диапазона должен быть позже начала": "The end of the range must be later than its start.",
    "Слишком много вариантов — увеличьте шаг или сузьте диапазон": "Too many candidates. Increase the step or narrow the range.",
}


def language(request):
    explicit = request.query_params.get("lang")
    if explicit in ("ru", "en"):
        return explicit
    return "en" if request.headers.get("accept-language", "").lower().startswith("en") else "ru"


def translate(detail, lang):
    if lang != "en" or not isinstance(detail, str):
        return detail
    if detail.startswith("Неизвестный часовой пояс: "):
        return "Unknown time zone: " + detail.split(": ", 1)[1]
    if detail.startswith("Value error, "):
        return "Value error, " + translate(detail[len("Value error, "):], lang)
    return TRANSLATIONS.get(detail, detail)
