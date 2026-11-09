# Saumya Solar Energy Solution

## Production-Grade Multi-Tenant Solar ERP, E-Commerce, CRM and Service Management Platform

For **Saumya Solar Energy Solution**, the application should be designed as a **multi-tenant Solar ERP + e-commerce +
CRM + service-management SaaS platform**, not just a normal solar website.

The key production-grade requirement is **tenant isolation**: every dealer/vendor gets their own secure workspace and
can access only their store, products, customers, quotations, orders, inventory, invoices, service requests, reports,
and team members.

## 1. User Types and Access Model

### Platform Super Admin

This is Saumya Solar Energy Solution's internal administration.

Can manage:

- Vendors/dealers
- Subscription plans
- Vendor onboarding
- Global solar package templates
- Product catalogue
- Vendor status
- Platform configuration
- User/role management
- Audit logs
- Platform reports
- Notification templates
- Support requests
- System health

### Vendor / Dealer Admin

Each registered solar dealer/company receives an isolated tenant.

Example:

```text
Vendor A
 ├── Vendor A Products
 ├── Vendor A Customers
 ├── Vendor A Orders
 ├── Vendor A Inventory
 └── Vendor A Employees

Vendor B
 ├── Vendor B Products
 ├── Vendor B Customers
 ├── Vendor B Orders
 ├── Vendor B Inventory
 └── Vendor B Employees
```

**Vendor A must never be able to view, search, modify, export, or access Vendor B's data**, including through APIs.

### Vendor Employees

Vendor Admin should be able to create roles such as:

- Sales Manager
- Sales Executive
- Inventory Manager
- Service Engineer
- Accountant
- Installer
- Operations Manager

Use **RBAC plus tenant-level authorization**.

### Customer

Customers can:

- Browse public site
- Register/login
- Browse solar products/packages
- Request quotation
- Place/track orders
- View/download quotations
- View/download invoices
- Raise service requests
- Track installation/service status
- Submit enquiries
- View warranty/AMC information
- Receive notifications

## 2. Public/Customer Website

### Home

- Company introduction
- Solar solutions
- Featured packages
- Why choose us
- Solar benefits
- Savings/ROI calculator
- Testimonials
- Installation gallery
- Call-to-action for quotation

### Solar Packages

For example:

```text
2 kW Residential Package
3 kW Residential Package
5 kW Residential Package
10 kW Commercial Package
Custom Industrial Package
```

Each package should support:

- Price
- System capacity
- Panel manufacturer/model
- Panel quantity
- Inverter
- Battery option
- Estimated generation
- Warranty
- Installation included/not included
- Subsidy eligibility/configuration
- Technical specifications
- Images/documents

Do not hard-code package attributes. Make them configurable.

### Product Catalogue

Categories could include:

- Solar panels
- Inverters
- Batteries
- Mounting structures
- Cables
- AC/DC distribution boxes
- Solar accessories

Support search, filtering, pricing, stock availability and technical specifications.

### Quotation

A customer should be able to submit:

```text
Name
Mobile
Email
Address
City/PIN code
Property type
Monthly electricity bill
Required capacity
Roof type
Available roof area
Message
```

Dealer can convert:

```text
Enquiry
   ↓
Lead
   ↓
Quotation
   ↓
Customer Approval
   ↓
Order
   ↓
Payment
   ↓
Installation
   ↓
Commissioning
   ↓
Warranty/Service
```

This workflow is important for a real solar business platform.

## 3. Customer Portal

After login:

```text
Dashboard
My Quotations
My Orders
My Invoices
My Payments
My Installations
My Solar Systems
Service Requests
Warranty
Documents
Notifications
Profile
```

An order should have a proper lifecycle, for example:

```text
Draft
→ Quotation Sent
→ Quotation Accepted
→ Order Confirmed
→ Payment Pending
→ Payment Received
→ Material Allocated
→ Installation Scheduled
→ Installation Started
→ Installation Completed
→ Commissioned
→ Closed
```

Keep status transitions configurable rather than relying only on free-text updates.

## 4. Vendor/Dealer Dashboard

The vendor dashboard could show:

```text
Today's Leads                 12
Open Quotations               18
Confirmed Orders               7
Installation Pending           6
Open Service Requests          4
Low Stock Items                8
Monthly Sales            ₹8,45,000
Outstanding Payments     ₹1,30,000
```

Charts:

- Monthly revenue
- Orders by status
- Leads conversion
- Product sales
- Installation status
- Service ticket status
- Inventory movement

## 5. CRM and Enquiry Management

This should be a significant module.

Dealer should manage:

- Enquiries
- Leads
- Customers
- Follow-ups
- Notes
- Sales assignment
- Lead source
- Quotation history
- Conversion tracking

Example pipeline:

```text
New Lead
 → Contacted
 → Site Visit Scheduled
 → Site Survey Completed
 → Quotation Sent
 → Negotiation
 → Won / Lost
```

## 6. Quotation Management

This is particularly useful for solar dealers.

Dealer selects:

```text
Customer
+
Solar Package
+
Panel
+
Inverter
+
Battery
+
Installation
+
Additional Material
+
Discount
+
Taxes
```

System generates:

**Quotation PDF**

Include:

- Dealer branding/logo
- Customer information
- Product/package details
- Pricing
- Taxes
- Terms and conditions
- Warranty
- Payment schedule
- Quotation validity

Then allow the quotation to be emailed to the customer.

## 7. Order Management

Admin/vendor should be able to:

- Create orders
- Convert quotation to order
- Assign salesperson
- Assign installer
- Record payment
- Allocate inventory
- Schedule installation
- Upload documents
- Generate invoice
- Track order status
- Cancel/refund where permitted

Maintain a complete order-status history.

## 8. Inventory Management

A production version should support:

### Products

```text
SKU
Manufacturer
Model
Category
Serial Number
Purchase Price
Selling Price
GST/Tax configuration
Available Quantity
Reserved Quantity
Reorder Level
Warehouse
```

### Inventory Operations

- Stock in
- Stock out
- Stock transfer
- Stock reservation
- Stock adjustment
- Purchase receipt
- Return
- Damaged stock

Also generate **low-stock alerts**.

## 9. Installation Management

Solar installation should be its own business workflow.

An installation record could contain:

```text
Order
Customer
Site address
Assigned engineer
Installation team
Scheduled date
System capacity
Panel serial numbers
Inverter serial number
Installation checklist
Site photographs
Commissioning date
Customer sign-off
```

Support site survey and installation checklists.

## 10. Service Management

Customer raises:

```text
Service Request
   ↓
Ticket Created
   ↓
Assigned to Engineer
   ↓
Visit Scheduled
   ↓
Work Started
   ↓
Resolved
   ↓
Customer Confirmation
   ↓
Closed
```

Ticket fields:

- Ticket number
- Category
- Priority
- Solar installation
- Product/serial number
- Warranty status
- Description
- Photos/documents
- Assigned engineer
- Appointment
- Resolution
- Resolution time

## 11. Vendor Management

Platform Admin should manage:

```text
Vendor Profile
Store Information
Logo
GST/Tax ID
Address
Contact information
Subscription
Users
Products
Orders
Customers
Storage usage
Status
```

Possible lifecycle:

```text
Pending
→ Verified
→ Active
→ Suspended
→ Closed
```

## 12. Team Management

Each vendor can create employees and assign roles.

For example:

| Role              | Orders | Inventory | Service  | Reports   |
|-------------------|--------|-----------|----------|-----------|
| Vendor Admin      | Full   | Full      | Full     | Full      |
| Sales             | Manage | View      | View     | Sales     |
| Inventory Manager | View   | Manage    | View     | Inventory |
| Service Engineer  | View   | View      | Assigned | Service   |
| Accountant        | View   | View      | View     | Financial |

Implement permission-level controls such as:

```text
PRODUCT_CREATE
PRODUCT_UPDATE
PRODUCT_DELETE
ORDER_CREATE
ORDER_UPDATE
ORDER_CANCEL
INVOICE_CREATE
INVENTORY_UPDATE
REPORT_EXPORT
USER_MANAGE
```

## 13. Multi-Tenant Architecture

I recommend a hierarchy such as:

```text
                   SAUMYA SOLAR PLATFORM
                           │
             ┌─────────────┼─────────────┐
             │             │             │
          Vendor A      Vendor B      Vendor C
             │             │             │
       ┌─────┼─────┐       │       ┌─────┼─────┐
       │     │     │       │       │     │     │
    Users Products Orders Products Users Products Orders
```

Most tenant-owned tables should have:

```text
tenant_id
```

For example:

```text
product
order
customer
quotation
invoice
inventory
service_request
employee
installation
payment
```

Backend authorization should enforce:

```text
authenticatedUser.tenantId == resource.tenantId
```

**Never depend on hiding data in React.**

Something like this is unsafe:

```http
GET /orders?vendorId=20
```

because a malicious user may change it to:

```http
GET /orders?vendorId=21
```

The backend should resolve the tenant from the authenticated identity and authorization context.

## 14. Reporting

Platform reports:

- Vendors
- Active vendors
- Total sales
- Platform orders
- Subscription usage
- Product/package performance

Vendor reports:

- Daily/monthly sales
- Revenue
- Outstanding payments
- Lead conversion
- Inventory status
- Low stock
- Product sales
- Installation performance
- Service tickets

Support:

```text
Dashboard
PDF Export
Excel/CSV Export
```

## 15. Communication System

Centralize business notifications.

Events:

```text
Quotation Created
Quotation Accepted
Order Confirmed
Payment Received
Installation Scheduled
Installation Completed
Service Ticket Created
Service Ticket Resolved
Invoice Generated
```

Channels:

```text
Email
SMS
WhatsApp
In-app notification
```

Design providers behind interfaces so email/SMS/WhatsApp vendors can be changed later without rewriting business logic.

## 16. Blog and CMS

Instead of hard-coding website content, provide lightweight CMS capabilities.

Admin/vendor can manage:

- Blog
- Homepage banners
- FAQs
- Testimonials
- Contact details
- About Us
- Terms & Conditions
- Privacy Policy
- Solar education articles

Blog lifecycle:

```text
Draft → Review → Published → Archived
```

## 17. Recommended Technical Architecture

Since the current technology stack is **Java 21 + Spring Boot + React + MySQL**, it can be retained.

```text
                   React Web Application
                           │
                        HTTPS
                           │
                    Spring Boot API
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
 Authentication       Business APIs       Notifications
        │                  │                  │
       JWT           Order/Product         Email/SMS
       RBAC          CRM/Service
        │                  │
        └──────────────────┼──────────────────┘
                           │
                         MySQL
                           │
                    Object Storage
                     Images/PDFs
```

For the first production release, use a **modular monolith**, not microservices.

Modules:

```text
auth
tenant
user
vendor
customer
product
solar-package
lead
quotation
order
inventory
installation
invoice
payment
service
notification
report
blog
audit
```

Modules can be split into microservices later if actual scale requires it.

## 18. Production-Grade Requirements

Do not limit the requirements to UI functionality. Add these explicitly.

### Security

- Spring Security
- JWT/OAuth2
- RBAC
- Tenant isolation
- Secure password hashing
- Rate limiting
- Input validation
- File-upload restrictions
- Audit logging
- Login/session management
- Password reset
- Account lockout/rate controls
- HTTPS
- Secrets outside source control

Design against **OWASP Top 10** risks.

### Audit Trail

Important operations should record:

```text
Who?
What?
When?
Tenant?
Old Value?
New Value?
IP/device context where appropriate
```

Especially:

- Product price changes
- Order status changes
- Inventory adjustments
- Payment updates
- User/role changes
- Invoice changes

### Reliability

Include:

- Database migrations using Flyway or Liquibase
- Database backup/restore process
- Connection pooling
- Retry/idempotency for integrations
- Global exception handling
- Structured logging
- Health endpoints
- Monitoring/alerting
- Pagination
- Caching where justified

## 19. Core Database Entities

A good initial domain model would be:

```text
Tenant
Vendor
User
Role
Permission

Customer
Address

Product
ProductCategory
SolarPackage
PackageItem
Inventory
InventoryTransaction

Lead
SiteSurvey
Quotation
QuotationItem

Order
OrderItem
OrderStatusHistory

Payment
Invoice

Installation
InstallationTeam
InstallationChecklist

ServiceRequest
ServiceVisit

Notification

Blog
Document

AuditLog
```

Most business entities should reference:

```text
tenant_id
created_at
created_by
updated_at
updated_by
version
```

where appropriate.

## 20. Product Roadmap

Do **not build everything at once**.

### Phase 1: Commercial MVP

Build:

1. Multi-tenant architecture
2. Authentication
3. Vendor management
4. User/RBAC management
5. Customer management
6. Solar product/package management
7. Enquiries/leads
8. Quotations
9. Orders
10. Basic inventory
11. Invoice generation
12. Email notifications
13. Basic dashboard

### Phase 2: Operations

Add:

- Site survey
- Installation scheduling
- Engineer assignment
- Service tickets
- Warranty
- Inventory transactions
- Payment tracking
- Advanced reporting
- SMS/WhatsApp

### Phase 3: Enterprise SaaS

Add:

- Vendor subscription/plans
- Custom vendor branding
- Multiple branches
- Multiple warehouses
- Approval workflows
- Advanced audit
- Analytics
- Mobile/PWA
- Accounting integration
- Payment gateway
- API integrations

## Most Important Design Decision

Think of the product as:

> **One SaaS platform → Many Solar Vendors → Many Employees → Many Customers**

rather than:

> One solar company's website.

That distinction will make the application genuinely sellable to other solar dealers.

For the existing **Spring Boot 3 + Java 21 + React + MySQL** application, the strongest next step is to turn these
requirements into a complete **production-ready SRS/HLD with modules, roles and permissions, database design, REST APIs,
screens, workflows, security requirements, tenant-isolation strategy, and acceptance criteria** before adding more
features.
