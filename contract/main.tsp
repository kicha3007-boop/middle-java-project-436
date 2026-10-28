import "@typespec/http";
import "@typespec/openapi";
import "@typespec/openapi3";

using TypeSpec.Http;
using TypeSpec.OpenAPI;

@service(#{ title: "Online Store API" })
@info(#{ version: "1.0.0" })
@server("http://localhost:8080", "Локальный запуск")
namespace OnlineStoreApi;

// ---------- Общие модели ----------

@doc("Денежная сумма в целых рублях.")
model Money {
  amount: int32;
  currency: "RUB";
}

@doc("Закрытый словарь типов ошибок. Новый случай начинается с правки этого перечисления.")
union ProblemType {
  @doc("Неизвестный email или неверный пароль (401).")
  invalidCredentials: "https://hexlet.io/problems/invalid-credentials",

  @doc("Email уже занят (409).")
  emailTaken: "https://hexlet.io/problems/email-taken",

  @doc("Доставка без адреса (400).")
  addressRequired: "https://hexlet.io/problems/address-required",

  @doc("Часть товаров не найдена или недоступна (400).")
  unavailableItems: "https://hexlet.io/problems/unavailable-items",

  @doc("Тело или параметры запроса не прошли проверку (400).")
  validationError: "https://hexlet.io/problems/validation-error",

  @doc("Нужна активная сессия (401).")
  unauthorized: "https://hexlet.io/problems/unauthorized",

  @doc("Ресурс не найден (404).")
  notFound: "https://hexlet.io/problems/not-found",

  @doc("Непредвиденная ошибка сервера (500).")
  internalError: "https://hexlet.io/problems/internal-error",
}

@doc("Ошибка в формате Problem Details (RFC 9457). Текст для пользователя фронтенд выбирает по type.")
model ProblemDetails {
  type: ProblemType;

  @doc("Техническое название ошибки для разработчика.")
  title: string;

  status: int32;

  @doc("Техническое пояснение конкретного случая.")
  detail?: string;

  @doc("Все товары, из-за которых заказ отклонён (только для unavailable-items).")
  unavailableProductIds?: string[];
}

@error
model BadRequestError {
  @statusCode statusCode: 400;
  @body body: ProblemDetails;
}

@error
model UnauthorizedError {
  @statusCode statusCode: 401;
  @body body: ProblemDetails;
}

@error
model NotFoundError {
  @statusCode statusCode: 404;
  @body body: ProblemDetails;
}

@error
model ConflictError {
  @statusCode statusCode: 409;
  @body body: ProblemDetails;
}

// ---------- Каталог ----------

model Category {
  id: string;

  @doc("Слаг латиницей без пробелов, например videokarty.")
  slug: string;

  name: string;
}

model Product {
  @doc("Идентификатор; им заканчивается адрес страницы товара.")
  id: string;

  slug: string;
  name: string;
  description: string;
  price: Money;
  categoryId: string;

  @doc("Путь к изображению. У товара без изображения поля нет.")
  imageUrl?: string;

  available: boolean;
}

model PageMeta {
  page: int32;
  perPage: int32;

  @doc("Число товаров, подходящих под фильтр, а не размер страницы.")
  total: int32;

  totalPages: int32;
}

model ProductPage {
  items: Product[];
  meta: PageMeta;
}

model Promo {
  id: string;
  title: string;
  text: string;

  @doc("Товар целиком, в том же виде, что в каталоге.")
  product: Product;
}

// ---------- Пользователь ----------

model User {
  id: string;
  email: string;
}

model Credentials {
  @format("email")
  email: string;

  @minLength(6)
  password: string;
}

// ---------- Заказ ----------

enum ShippingMethod {
  delivery,
  pickup,
}

model ShippingDetails {
  method: ShippingMethod;
  recipientName: string;
  phone: string;

  @doc("Обязателен при delivery.")
  address?: string;
}

@doc("Позиция корзины: только товар и количество, цены клиент не присылает.")
model CartItem {
  productId: string;

  @minValue(1)
  quantity: int32;
}

@doc("Позиция заказа — снимок названия и цены на момент покупки.")
model OrderItem {
  productId: string;
  productName: string;
  priceAtPurchase: Money;
  quantity: int32;
}

enum OrderStatus {
  paid,
}

model Order {
  id: string;
  status: OrderStatus;
  items: OrderItem[];
  shipping: ShippingDetails;

  @doc("Итог, посчитанный сервером по текущим ценам.")
  total: Money;

  @doc("Момент создания в UTC.")
  createdAt: utcDateTime;
}

model CreateOrderRequest {
  @minItems(1)
  items: CartItem[];

  shipping: ShippingDetails;
}

model Health {
  status: "ok";
}

// ---------- Операции ----------

@route("/api/health")
@tag("Service")
interface HealthCheck {
  @doc("Проверка живости, не зависит от базы.")
  @get
  get(): Health;
}

@route("/api/users")
@tag("Auth")
interface Users {
  @doc("Регистрация. Открывает сессию.")
  @post
  register(@body body: Credentials): {
    @statusCode statusCode: 201;
    @body user: User;
  } | BadRequestError | ConflictError;
}

@route("/api/session")
@tag("Auth")
interface Session {
  @doc("Вход. Открывает сессию.")
  @post
  create(@body body: Credentials): User | BadRequestError | UnauthorizedError;

  @doc("Выход. Завершает сессию на сервере.")
  @delete
  destroy(): {
    @statusCode statusCode: 204;
  };
}

@route("/api/me")
@tag("Auth")
interface Me {
  @doc("Текущий пользователь; аноним получает 401.")
  @get
  get(): User | UnauthorizedError;
}

@route("/api/categories")
@tag("Catalog")
interface Categories {
  @get
  list(): Category[];
}

@route("/api/promos")
@tag("Home")
interface Promos {
  @get
  list(): Promo[];
}

@route("/api/products")
@tag("Catalog")
interface Products {
  @doc("Каталог: фильтры комбинируются, фильтрация и пагинация — на сервере.")
  @get
  list(
    @doc("Слаг категории.")
    @query
    category?: string,

    @doc("Подстрока названия без учёта регистра.")
    @query
    q?: string,

    @doc("Нижняя граница цены включительно.")
    @query
    priceMin?: int32,

    @doc("Верхняя граница цены включительно.")
    @query
    priceMax?: int32,

    @doc("true — только доступные, false — только недоступные, нет параметра — все.")
    @query
    available?: boolean,

    @query @minValue(1) page?: int32 = 1,
    @query @minValue(1) @maxValue(48) perPage?: int32 = 6,
  ): ProductPage | BadRequestError;

  @get
  @route("/{id}")
  getById(@path id: string): Product | NotFoundError;
}

@route("/api/orders")
@tag("Orders")
interface Orders {
  @doc("Оформление заказа. Сервер сам берёт цены и считает итог; отказ — целиком.")
  @post
  create(@body body: CreateOrderRequest): {
    @statusCode statusCode: 201;
    @body order: Order;
  } | BadRequestError | UnauthorizedError;

  @doc("Заказы текущего пользователя, новые первыми.")
  @get
  list(): Order[] | UnauthorizedError;

  @doc("Свой заказ; чужой или несуществующий — 404.")
  @get
  @route("/{id}")
  getById(@path id: string): Order | UnauthorizedError | NotFoundError;
}
